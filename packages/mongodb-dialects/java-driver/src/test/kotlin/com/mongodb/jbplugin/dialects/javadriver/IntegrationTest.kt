/**
 * Class that contains the JUnit5 extension to run tests
 * that use the IntelliJ Java parser.
 */

package com.mongodb.jbplugin.dialects.javadriver

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.*
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private const val MONGO_CLIENT = "com.mongodb.client.MongoClient"

@Retention(AnnotationRetention.RUNTIME)
@Test
annotation class ParsingTest(
    val fileName: String,
    @Language("java") val value: String,
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
    InvocationInterceptor,
    ParameterResolver {
    private val namespace = ExtensionContext.Namespace.create(IntegrationTestExtension::class.java)
    private val testFixtureKey = "TESTFIXTURE"

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

        ApplicationManager.getApplication().invokeLater {
            if (!JavaLibraryUtil.hasLibraryJar(testFixture.module, "org.mongodb:mongodb-driver-sync:5.1.1")) {
                PsiTestUtil.addProjectLibrary(
                    testFixture.module,
                    "mongodb-driver-sync",
                    listOf(
                        Path(
                            "src/test/resources/mongodb-driver-sync-5.1.1.jar",
                        ).toAbsolutePath().toString(),
                    ),
                )
            }
        }

        PsiTestUtil.addSourceRoot(testFixture.module, testFixture.project.guessProjectDir()!!)
    }

    override fun beforeEach(context: ExtensionContext) {
        val fixture = context.getStore(namespace).get(testFixtureKey) as CodeInsightTestFixture

        ApplicationManager.getApplication().invokeAndWait {
            val parsingTest = context.requiredTestMethod.getAnnotation(ParsingTest::class.java)
            val path = ModuleUtilCore.getModuleDirPath(fixture.module)
            val fileName = Path(path, "src", "main", "java", parsingTest.fileName).absolutePathString()
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
        val throwable: AtomicReference<Throwable?> = AtomicReference(null)
        val finished = AtomicBoolean(false)

        val fixture = extensionContext.getStore(namespace).get(testFixtureKey) as CodeInsightTestFixture
        val dumbService = fixture.project.getService(DumbService::class.java)
        dumbService.runWhenSmart {
            val result =
                runCatching {
                    val javaPsiFacade = JavaPsiFacade.getInstance(fixture.project)
                    val searchEverywhere = GlobalSearchScope.everythingScope(fixture.project)
                    var times = 0
                    while (javaPsiFacade.findClass(MONGO_CLIENT, searchEverywhere) == null && times < 10) {
                        System.err.println("Driver not loaded yet. Waiting for 100ms")
                        Thread.sleep(100)
                        times++
                    }
                    invocation.proceed()
                }
            result.onSuccess {
                finished.set(true)
            }

            result.onFailure {
                finished.set(true)
                throwable.set(it)
            }
        }

        while (!finished.get()) {
            Thread.sleep(1)
        }

        throwable.get()?.let { throw it }
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
        val fixture = extensionContext.getStore(namespace).get(testFixtureKey) as CodeInsightTestFixture

        return when (parameterContext.parameter.type) {
            Project::class.java -> fixture.project
            CodeInsightTestFixture::class.java -> fixture
            PsiFile::class.java -> fixture.file
            JavaPsiFacade::class.java -> JavaPsiFacade.getInstance(fixture.project)
            else -> TODO("Parameter of type ${parameterContext.parameter.type.canonicalName} is not supported.")
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
