/**
 * JUnit extension to run tests that depend on code insights without building the whole IDE.
 * They are more lightweight than UI tests.
 */

package com.mongodb.jbplugin.fixtures

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.mongodb.client.MongoClient
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.*
import java.lang.reflect.Method
import java.net.URI
import java.net.URL
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * Annotation to add to the test function.
 */
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
@ExtendWith(CodeInsightTestExtension::class)
annotation class CodeInsightTest

/**
 * Extension implementation. Must not be used directly.
 */
internal class CodeInsightTestExtension :
    BeforeAllCallback,
    AfterAllCallback,
    BeforeEachCallback,
    InvocationInterceptor,
    ParameterResolver {
    private val namespace = ExtensionContext.Namespace.create(CodeInsightTestExtension::class.java)
    private val testFixtureKey = "TESTFIXTURE"
    private val testPathKey = "TESTPATH"

    override fun beforeAll(context: ExtensionContext) {
        ApplicationManager.setApplication(null)

        val projectFixture =
            IdeaTestFixtureFactory
                .getFixtureFactory()
                .createLightFixtureBuilder(context.requiredTestClass.simpleName)
                .fixture

        val tempDirTestFixtureImpl = TempDirTestFixtureImpl()
        val testFixture =
            IdeaTestFixtureFactory
                .getFixtureFactory()
                .createCodeInsightFixture(
                    projectFixture,
                    tempDirTestFixtureImpl,
                ).apply {
                    testDataPath = tempDirTestFixtureImpl.tempDirPath
                }

        context.getStore(namespace).put(testFixtureKey, testFixture)
        testFixture.setUp()

        ApplicationManager.getApplication().invokeAndWait {
            if (!JavaLibraryUtil.hasLibraryJar(testFixture.module, "org.mongodb:mongodb-driver-sync:5.1.1")) {
                runCatching {
                    PsiTestUtil.addProjectLibrary(
                        testFixture.module,
                        "mongodb-driver-sync",
                        listOf(
                            Path(
                                pathToJavaDriver(),
                            ).toAbsolutePath().toString(),
                        ),
                    )
                }
            }
        }

        PsiTestUtil.addSourceRoot(testFixture.module, testFixture.project.guessProjectDir()!!)
        val tmpRootDir = testFixture.tempDirFixture.getFile(".")!!
        PsiTestUtil.addSourceRoot(testFixture.module, tmpRootDir)
        context.getStore(namespace).put(testPathKey, tmpRootDir.path)
    }

    override fun beforeEach(context: ExtensionContext) {
        val fixture = context.getStore(namespace).get(testFixtureKey) as CodeInsightTestFixture
        val modulePath = context.getStore(namespace).get(testPathKey).toString()

        ApplicationManager.getApplication().invokeAndWait {
            val parsingTest = context.requiredTestMethod.getAnnotation(ParsingTest::class.java)
            val fileName = Path(modulePath, "src", "main", "java", parsingTest.fileName).absolutePathString()

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

        throwable.get()?.let {
            System.err.println(it.message)
            it.printStackTrace(System.err)
            throw it
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
        val fixture = extensionContext.getStore(namespace).get(testFixtureKey) as CodeInsightTestFixture

        return when (parameterContext.parameter.type) {
            Project::class.java -> fixture.project
            CodeInsightTestFixture::class.java -> fixture
            PsiFile::class.java -> fixture.file
            JavaPsiFacade::class.java -> JavaPsiFacade.getInstance(fixture.project)
            else -> TODO("Parameter of type ${parameterContext.parameter.type.canonicalName} is not supported.")
        }
    }

    private fun pathToJavaDriver(): String {
        val classResource: URL =
            MongoClient::class.java.getResource(MongoClient::class.java.getSimpleName() + ".class")
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
