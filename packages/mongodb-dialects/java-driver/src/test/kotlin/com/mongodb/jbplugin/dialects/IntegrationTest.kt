package com.mongodb.jbplugin.dialects

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
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
    BeforeEachCallback,
    AfterEachCallback,
    InvocationInterceptor,
    ParameterResolver {
    private val namespace = ExtensionContext.Namespace.create(IntegrationTestExtension::class.java)
    private val testFixtureKey = "TESTFIXTURE"

    override fun beforeEach(context: ExtensionContext) {
        val projectFixture =
            IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder(context.requiredTestClass.simpleName).fixture

        val testFixture =
            IdeaTestFixtureFactory.getFixtureFactory()
                .createCodeInsightFixture(
                    projectFixture,
                )

        context.getStore(namespace).put(testFixtureKey, testFixture)
        testFixture.setUp()

        PsiTestUtil.addProjectLibrary(
            testFixture.module,
            "mongodb-driver-sync",
            listOf(
                Path(
                    "src/test/resources/mongodb-driver-sync-5.1.1.jar",
                ).toAbsolutePath().toString(),
            ),
        )

        PsiTestUtil.addSourceRoot(testFixture.module, testFixture.project.guessProjectDir()!!)

        ApplicationManager.getApplication().invokeAndWait {
            val parsingTest = context.requiredTestMethod.getAnnotation(ParsingTest::class.java)
            testFixture.configureByText(
                parsingTest.fileName,
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

        try {
            val fixture = extensionContext.getStore(namespace).get(testFixtureKey) as CodeInsightTestFixture
            val dumbService = fixture.project.getService(DumbService::class.java)
            dumbService.runWhenSmart {
                invocation.proceed()
                finished.set(true)
            }
        } catch (t: Throwable) {
            throwable.set(t)
        }

        while (!finished.get()) {
            Thread.sleep(1)
        }

        val t = throwable.get()
        if (t != null) {
            throw t
        }
    }

    override fun afterEach(context: ExtensionContext) {
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

val PsiFile.testQuery: PsiMethodCallExpression
    get() = PsiTreeUtil.findChildOfType(this, PsiMethodCallExpression::class.java)!!
