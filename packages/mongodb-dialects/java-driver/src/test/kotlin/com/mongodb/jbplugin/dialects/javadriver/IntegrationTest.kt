/**
 * Class that contains the JUnit5 extension to run tests
 * that use the IntelliJ Java parser.
 */

package com.mongodb.jbplugin.dialects.javadriver

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.AcceptedLanguageLevelsSettings
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.*
import java.lang.reflect.Method
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@Retention(AnnotationRetention.RUNTIME)
@Test
annotation class ParsingTest(
    val fileName: String,
    @Language("java") val value: String,
)

@Retention(AnnotationRetention.RUNTIME)
annotation class WithFile(
    val fileName: String,
    @Language("java") val value: String,
)

/**
 * Annotation to be used in the test, at the class level.
 *
 * @see com.mongodb.jbplugin.accessadapter.datagrip.adapter.DataGripMongoDbDriverTest
 */
@ExtendWith(IntegrationTestExtension::class)
@TestDataPath("${'$'}CONTENT_ROOT/testData")
annotation class IntegrationTest

/**
 * Extension implementation. Must not be used directly.
 */
internal class IntegrationTestExtension :
    BeforeAllCallback,
    AfterAllCallback,
    BeforeEachCallback,
    InvocationInterceptor,
    ParameterResolver {
    private val namespace = ExtensionContext.Namespace.create(IntegrationTestExtension::class.java)
    private val testFixtureKey = "TESTFIXTURE"
    private val testPathKey = "TESTPATH"

    override fun beforeAll(context: ExtensionContext) {
        TestApplicationManager.getInstance()
        val projectDescriptor = MongoDbProjectDescriptor(LanguageLevel.JDK_21)

        val projectFixture =
            IdeaTestFixtureFactory
                .getFixtureFactory()
                .createLightFixtureBuilder(projectDescriptor, context.requiredTestClass.simpleName)
                .fixture

        val testFixture =
            IdeaTestFixtureFactory
                .getFixtureFactory()
                .createCodeInsightFixture(
                    projectFixture,
                )

        context.getStore(namespace).put(testFixtureKey, testFixture)
        testFixture.setUp()

        val projectExt = LanguageLevelProjectExtension.getInstance(projectFixture.project)
        projectExt.languageLevel = LanguageLevel.JDK_21
        IndexingTestUtil.waitUntilIndexesAreReady(projectFixture.project)

        PsiTestUtil.addSourceRoot(testFixture.module, testFixture.project.guessProjectDir()!!)
        val tmpRootDir = testFixture.tempDirFixture.getFile(".")!!
        PsiTestUtil.addSourceRoot(testFixture.module, tmpRootDir)
        context.getStore(namespace).put(testPathKey, tmpRootDir.path)
    }

    override fun beforeEach(context: ExtensionContext) {
        val fixture = context.getStore(namespace).get(testFixtureKey) as CodeInsightTestFixture
        val modulePath = context.getStore(namespace).get(testPathKey).toString()

        ApplicationManager.getApplication().invokeAndWait {
            val parsingTest: ParsingTest? = context.requiredTestMethod.getAnnotation(
                ParsingTest::class.java
            )
            val withFile: WithFile? = context.requiredTestMethod.getAnnotation(WithFile::class.java)

            val cfgFileName = parsingTest?.fileName ?: withFile?.fileName ?: return@invokeAndWait
            val cfgContents = parsingTest?.value ?: withFile?.value ?: return@invokeAndWait

            val fileName = Path(
                modulePath,
                "src",
                "main",
                "java",
                cfgFileName
            ).absolutePathString()

            fixture.configureByText(
                fileName,
                cfgContents
            )
        }
    }

    override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        ApplicationManager.getApplication().invokeAndWait {
            val fixture = extensionContext.getStore(
                namespace
            ).get(testFixtureKey) as CodeInsightTestFixture
            val dumbService = DumbService.getInstance(fixture.project)

            // Run only when the code has been analysed
            runBlocking<Void> {
                suspendCancellableCoroutine { callback ->
                    dumbService.runWhenSmart {
                        runCatching {
                            callback.resume(invocation.proceed())
                        }.onFailure {
                            callback.resumeWithException(it)
                        }
                    }
                }
            }
        }
    }

    override fun afterAll(context: ExtensionContext) {
        val testFixture = context.getStore(namespace).get(testFixtureKey) as CodeInsightTestFixture

        ApplicationManager.getApplication().invokeAndWait {
            testFixture.tearDown()
        }
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean =
        parameterContext.parameter.type == Project::class.java ||
            parameterContext.parameter.type == CodeInsightTestFixture::class.java ||
            parameterContext.parameter.type == PsiFile::class.java ||
            parameterContext.parameter.type == JavaPsiFacade::class.java

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Any {
        val fixture = extensionContext.getStore(
            namespace
        ).get(testFixtureKey) as CodeInsightTestFixture

        return when (parameterContext.parameter.type) {
            Project::class.java -> fixture.project
            CodeInsightTestFixture::class.java -> fixture
            PsiFile::class.java -> fixture.file
            JavaPsiFacade::class.java -> JavaPsiFacade.getInstance(fixture.project)
            else -> TODO(
                "Parameter of type ${parameterContext.parameter.type.canonicalName} is not supported."
            )
        }
    }
}

fun PsiFile.getClassByName(name: String): PsiClass =
    childrenOfType<PsiClass>().first {
        it.name == name
    }

fun PsiFile.getQueryAtMethod(
    className: String,
    methodName: String,
): PsiElement {
    val actualClass = getClassByName(className)
    val method = actualClass.allMethods.first { it.name == methodName }
    val returnExpr = PsiUtil.findReturnStatements(method).last()
    return returnExpr.returnValue!!
}

fun PsiFile.caret(): PsiElement = PsiTreeUtil.findChildrenOfType(
    this,
    PsiLiteralExpression::class.java
)
    .first {
        it.textMatches(""""|"""")
    }

private class MongoDbProjectDescriptor(
    val languageLevel: LanguageLevel
) : DefaultLightProjectDescriptor() {
    override fun setUpProject(
        project: Project,
        handler: SetupHandler
    ) {
        if (languageLevel.isPreview || languageLevel == LanguageLevel.JDK_X) {
            AcceptedLanguageLevelsSettings.allowLevel(project, languageLevel)
        }

        withRepositoryLibrary("org.mongodb:mongodb-driver-sync:5.1.0")
        super.setUpProject(project, handler)
    }

    override fun getSdk(): Sdk {
        return IdeaTestUtil.getMockJdk(languageLevel.toJavaVersion())
    }

    override fun configureModule(
        module: Module,
        model: ModifiableRootModel,
        contentEntry: ContentEntry
    ) {
        model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel =
            languageLevel

        addJetBrainsAnnotations(model)
        super.configureModule(module, model, contentEntry)
    }
}
