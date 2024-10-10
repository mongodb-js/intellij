/**
 * Class that contains the JUnit5 extension to run tests
 * that use the IntelliJ Java parser.
 */

package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.*
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import java.lang.reflect.Method
import java.net.URI
import java.net.URL
import java.nio.file.Paths
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
@Test
annotation class AdditionalFile(
    val fileName: String,
    val value: String,
)

/**
 * Annotation to be used in the test, at the class level.
 *
 * @see com.mongodb.jbplugin.accessadapter.datagrip.adapter.DataGripMongoDbDriverTest
 */
@ExtendWith(IntegrationTestExtension::class)
annotation class IntegrationTest

/**
 * Extension implementation. Must not be used directly.
 */
internal class IntegrationTestExtension :
    BeforeAllCallback,
    AfterAllCallback,
    BeforeEachCallback,
    AfterEachCallback,
    InvocationInterceptor,
    ParameterResolver {
    private val namespace = ExtensionContext.Namespace.create(IntegrationTestExtension::class.java)
    private val testFixtureKey = "TESTFIXTURE"
    private val testPathKey = "TESTPATH"

    override fun beforeAll(context: ExtensionContext) {
        val projectFixture =
            IdeaTestFixtureFactory
                .getFixtureFactory()
                .createLightFixtureBuilder(context.requiredTestClass.simpleName)
                .fixture

        val testFixture =
            IdeaTestFixtureFactory
                .getFixtureFactory()
                .createCodeInsightFixture(
                    projectFixture,
                )

        context.getStore(namespace).put(testFixtureKey, testFixture)
        testFixture.setUp()

        // Add the Spring libraries to the project, the real jars, that are
        // defined in Gradle as test dependencies.
        ApplicationManager.getApplication().invokeAndWait {
            val module = testFixture.module

            if (!JavaLibraryUtil.hasLibraryJar(
                    module,
                    "org.springframework.data:spring-data-mongodb:4.3.2"
                )
            ) {
                runCatching {
                    PsiTestUtil.addProjectLibrary(
                        module,
                        "org.springframework.data:spring-data-mongodb:4.3.2",
                        listOf(pathToClassJarFile(MongoTemplate::class.java)),
                    )

                    PsiTestUtil.addProjectLibrary(
                        module,
                        "org.springframework.data:spring-data-mongodb-mapping:4.3.2",
                        listOf(pathToClassJarFile(Document::class.java)),
                    )
                }
            }
        }

        // Add source folders to the project
        PsiTestUtil.addSourceRoot(testFixture.module, testFixture.project.guessProjectDir()!!)
        val tmpRootDir = testFixture.tempDirFixture.getFile(".")!!
        PsiTestUtil.addSourceRoot(testFixture.module, tmpRootDir)
        context.getStore(namespace).put(testPathKey, tmpRootDir.path)
    }

    override fun beforeEach(context: ExtensionContext) {
        val fixture = context.getStore(namespace).get(testFixtureKey) as CodeInsightTestFixture
        val modulePath = context.getStore(namespace).get(testPathKey).toString()

        // Configure an editor with the source code from @ParsingTest
        ApplicationManager.getApplication().invokeAndWait {
            context.requiredTestMethod.getAnnotationsByType(AdditionalFile::class.java).forEach {
                fixture.addFileToProject(it.fileName, it.value)
            }

            val parsingTest =
                context.requiredTestMethod.getAnnotation(ParsingTest::class.java)
                    ?: return@invokeAndWait

            val fileName = Path(
                modulePath,
                "src",
                "main",
                "java",
                parsingTest.fileName
            ).absolutePathString()

            fixture.configureByText(
                fileName,
                parsingTest.value,
            )
        }
    }

    override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        val fixture = extensionContext.getStore(
            namespace
        ).get(testFixtureKey) as CodeInsightTestFixture
        val dumbService = DumbService.getInstance(fixture.project)

        // Run only when the code has been analysed
        runBlocking {
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

    override fun afterEach(context: ExtensionContext) {
        val fixture = context.getStore(
            namespace
        ).get(testFixtureKey) as CodeInsightTestFixture

        context.requiredTestMethod.getAnnotationsByType(AdditionalFile::class.java).forEach {
            WriteCommandAction.runWriteCommandAction(fixture.project) {
                fixture.findFileInTempDir(it.fileName).delete(this)
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

    private fun pathToClassJarFile(javaClass: Class<*>): String {
        val classResource: URL =
            javaClass.getResource(javaClass.getSimpleName() + ".class")
                ?: throw RuntimeException("class resource is null")
        val url: String = classResource.toString()
        if (url.startsWith("jar:file:")) {
            // extract 'file:......jarName.jar' part from the url string
            val path = url.replace("^jar:(file:.*[.]jar)!/.*".toRegex(), "$1")
            try {
                return Paths.get(URI(path)).toString()
            } catch (e: Exception) {
                throw RuntimeException("Invalid Jar File URL String")
            }
        }
        throw RuntimeException("Invalid Jar File URL String")
    }
}

fun PsiFile.getClassByName(name: String): PsiClass =
    childrenOfType<PsiClass>().first {
        it.name == name
    }

fun PsiFile.getQueryAtMethod(
    className: String,
    methodName: String,
): PsiExpression {
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
