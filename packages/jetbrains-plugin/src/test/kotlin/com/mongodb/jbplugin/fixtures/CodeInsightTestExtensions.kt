/**
 * JUnit extension to run tests that depend on code insights without building the whole IDE.
 * They are more lightweight than UI tests.
 */

package com.mongodb.jbplugin.fixtures

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import org.bson.types.ObjectId
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

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
    BeforeEachCallback,
    AfterEachCallback,
    InvocationInterceptor,
    ParameterResolver {
    private val namespace = ExtensionContext.Namespace.create(CodeInsightTestExtension::class.java)
    private val testFixtureKey = "TESTFIXTURE"

    override fun beforeEach(context: ExtensionContext) {
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

        ApplicationManager.getApplication().invokeAndWait {
            if (!JavaLibraryUtil.hasLibraryJar(testFixture.module, "org.mongodb:mongodb-driver-sync:5.1.1")) {
                val module = testFixture.module

                runCatching {
                    PsiTestUtil.addProjectLibrary(
                        module,
                        "org.mongodb:mongodb-driver-sync:5.1.0",
                        listOf(pathToClassJarFile(MongoClient::class.java)),
                    )

                    PsiTestUtil.addProjectLibrary(
                        module,
                        "org.mongodb:mongodb-driver-core:5.1.0",
                        listOf(pathToClassJarFile(Filters::class.java)),
                    )

                    PsiTestUtil.addProjectLibrary(
                        module,
                        "org.mongodb:bson:5.1.0",
                        listOf(pathToClassJarFile(ObjectId::class.java)),
                    )
                }
            }
        }

        val tmpRootDir = testFixture.tempDirFixture.getFile(".")!!

        PsiTestUtil.addSourceRoot(testFixture.module, testFixture.project.guessProjectDir()!!)
        PsiTestUtil.addSourceRoot(testFixture.module, tmpRootDir)

        val parsingTest = context.requiredTestMethod.getAnnotation(ParsingTest::class.java)

        ApplicationManager.getApplication().invokeAndWait {
            val fileName = Path(tmpRootDir.path, "src", "main", "java", parsingTest.fileName).absolutePathString()

            testFixture.configureByText(
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
            ApplicationManager.getApplication().invokeAndWait {
                val result =
                    runCatching {
                        invocation.proceed()
                    }

                result.onSuccess {
                    finished.set(true)
                }

                result.onFailure {
                    throwable.set(it)
                    finished.set(true)
                }
            }
        }

        runBlocking {
            withTimeout(10.seconds) {
                while (!finished.get()) {
                    delay(50.milliseconds)
                }
            }
        }

        throwable.get()?.let {
            System.err.println(it.message)
            it.printStackTrace(System.err)
            throw it
        }
    }

    override fun afterEach(context: ExtensionContext) {
        val testFixture = context.getStore(namespace).get(testFixtureKey) as CodeInsightTestFixture

        ApplicationManager.getApplication().invokeAndWait {
            runCatching {
                val fileEditorManager = FileEditorManager.getInstance(testFixture.project)
                fileEditorManager.openFiles.forEach {
                    fileEditorManager.closeFile(it)
                }
                fileEditorManager.allEditors.forEach {
                    Disposer.dispose(it)
                }

                testFixture.tearDown()
            }
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
