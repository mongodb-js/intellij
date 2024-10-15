/**
 * JUnit extension to run tests that depend on code insights without building the whole IDE.
 * They are more lightweight than UI tests.
 */

package com.mongodb.jbplugin.fixtures

import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.localDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Disposer
import com.intellij.pom.java.AcceptedLanguageLevelsSettings
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor.SetupHandler
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor.addJetBrainsAnnotations
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.editor.MongoDbVirtualFileDataSourceProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.*
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import java.lang.reflect.Method
import java.net.URI
import java.net.URL
import java.nio.file.Paths
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
    BeforeEachCallback,
    AfterEachCallback,
    InvocationInterceptor,
    ParameterResolver {
    private val namespace = ExtensionContext.Namespace.create(CodeInsightTestExtension::class.java)
    private val testFixtureKey = "TESTFIXTURE"

    // This function is probably gonna grow as we keep adding libraries for our test fixtures hence disabling this
    // lint warning here
    @Suppress("TOO_LONG_FUNCTION")
    override fun beforeEach(context: ExtensionContext) {
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

        val tmpRootDir = testFixture.tempDirFixture.getFile(".")!!

        PsiTestUtil.addSourceRoot(testFixture.module, testFixture.project.guessProjectDir()!!)
        PsiTestUtil.addSourceRoot(testFixture.module, tmpRootDir)

        val parsingTest = context.requiredTestMethod.getAnnotation(ParsingTest::class.java)

        ApplicationManager.getApplication().invokeAndWait {
            val fileName = Path(
                tmpRootDir.path,
                "src",
                "main",
                "java",
                parsingTest.fileName
            ).absolutePathString()

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

/**
 * Setups a connection that can be configured to return specific results for queries.
 *
 * @return
 */
@Suppress("TOO_LONG_FUNCTION")
fun CodeInsightTestFixture.setupConnection(): Pair<LocalDataSource, DataGripBasedReadModelProvider> {
    val dbPsiFacade = mock<DbPsiFacade>()
    val dbDataSource = mock<DbDataSource>()
    val dataSource = mockDataSource()
    val application = ApplicationManager.getApplication()
    val realConnectionManager = DatabaseConnectionManager.getInstance()
    val dbConnectionManager =
        mock<DatabaseConnectionManager>().also { cm ->
            `when`(cm.build(any(), any())).thenAnswer {
                realConnectionManager.build(
                    it.arguments[0] as Project,
                    it.arguments[1] as DatabaseConnectionPoint
                )
            }
        }
    val connection = mockDatabaseConnection(dataSource)
    val readModelProvider = mock<DataGripBasedReadModelProvider>()

    `when`(dbDataSource.localDataSource).thenReturn(dataSource)
    `when`(dbPsiFacade.findDataSource(any())).thenReturn(dbDataSource)
    `when`(dbConnectionManager.activeConnections).thenReturn(listOf(connection))

    file.virtualFile.putUserData(
        MongoDbVirtualFileDataSourceProvider.Keys.attachedDataSource,
        dataSource,
    )

    application.withMockedService(dbConnectionManager)
    project.withMockedService(readModelProvider)
    project.withMockedService(dbPsiFacade)

    return Pair(dataSource, readModelProvider)
}

/**
 * Set the current database name into the file.
 *
 * @param name
 */
fun CodeInsightTestFixture.specifyDatabase(name: String) {
    file.virtualFile.putUserData(
        MongoDbVirtualFileDataSourceProvider.Keys.attachedDatabase,
        name
    )
}

/**
 * Sets the current dialect into the file.
 *
 * @param dialect
 */
fun CodeInsightTestFixture.specifyDialect(dialect: Dialect<PsiElement, Project>) {
    file.virtualFile.putUserData(
        MongoDbVirtualFileDataSourceProvider.Keys.attachedDialect,
        dialect
    )
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
        withRepositoryLibrary("org.springframework.data:spring-data-mongodb:4.3.2")

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
