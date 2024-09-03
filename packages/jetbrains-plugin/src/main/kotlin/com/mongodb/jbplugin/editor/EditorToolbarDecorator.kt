package com.mongodb.jbplugin.editor

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.database.console.JdbcDriverManager
import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.model.RawDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.database.run.ConsoleRunConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.removeUserData
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiModificationTracker
import com.mongodb.jbplugin.dialects.ConnectionContextRequirement
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.editor.MongoDbVirtualFileDataSourceProvider.Keys
import com.mongodb.jbplugin.observability.probe.NewConnectionActivatedProbe

import java.util.WeakHashMap

import kotlinx.coroutines.CoroutineScope

private val log = logger<EditorToolbarDecorator>()

private val allDialects = listOf(
    JavaDriverDialect,
    SpringCriteriaDialect
)

/**
 * This decorator listens to an IntelliJ Editor lifecycle
 * and attaches our toolbar if necessary.
 *
 * @param coroutineScope
 */
class EditorToolbarDecorator(
    private val coroutineScope: CoroutineScope,
) : ProjectActivity,
    FileEditorManagerListener,
    DataSourceManager.Listener,
    JdbcDriverManager.Listener,
    PsiModificationTracker.Listener {
    private var inferredDatabase: String? = null
    private var guessedDialects: WeakHashMap<Editor, Dialect<PsiElement, Project>?> = WeakHashMap()

    // Initialised first, right after project opens up
    internal lateinit var toolbar: MdbJavaEditorToolbar

    // Initialised when this activity gets executed with the opened project
    internal lateinit var project: Project

    fun onConnected(dataSource: LocalDataSource) {
        val editor = this.getCurrentEditor() ?: return
        editor.virtualFile?.putUserData(Keys.attachedDataSource, dataSource)
        editor.virtualFile?.removeUserData(Keys.attachedDatabase)

        ApplicationManager.getApplication().invokeLater {
            val project = editor.project!!
            val session = DatabaseSessionManager.openSession(project, dataSource, null)
            val probe = NewConnectionActivatedProbe()
            probe.connected(session)

            toolbar.setSelectedDatabase(inferredDatabase)
            analyzeFileFromScratch()
        }
    }

    fun onDisconnected() {
        val editor = this.getCurrentEditor() ?: return
        editor.virtualFile?.removeUserData(Keys.attachedDataSource)
        editor.virtualFile?.removeUserData(Keys.attachedDatabase)
        analyzeFileFromScratch()
    }

    fun onDatabaseSelected(database: String) {
        val editor = this.getCurrentEditor() ?: return
        editor.virtualFile?.putUserData(Keys.attachedDatabase, database)
        analyzeFileFromScratch()
    }

    fun onDatabaseUnselected() {
        val editor = this.getCurrentEditor() ?: return
        editor.virtualFile?.removeUserData(Keys.attachedDatabase)
        analyzeFileFromScratch()
    }

    private fun guessDialect(): Dialect<PsiElement, Project>? {
        val editor = this.getCurrentEditor() ?: return null
        if (editor in guessedDialects) {
            return guessedDialects[editor]
        }

        ApplicationManager.getApplication().runReadAction {
            val psiFileResult = runCatching { PsiManager.getInstance(project).findFile(editor.virtualFile) }

            if (psiFileResult.isFailure) {
                guessedDialects[editor] = null
            }

            psiFileResult.onFailure {
                log.info("Could not find virtual file for current editor", it)
            }

            guessedDialects[editor] = psiFileResult.getOrNull()?.let { file ->
                allDialects.find { it.isUsableForSource(file) }
            }
        }
        return guessedDialects[editor]
    }

    private fun guessAndStoreDialect() {
        val editor = this.getCurrentEditor() ?: return
        project.getService(DumbService::class.java)?.runWhenSmart {
            val guessedDialect = guessDialect()
            guessedDialect?.let {
                editor.virtualFile?.putUserData(Keys.attachedDialect, guessedDialect)
            } ?: editor.virtualFile?.removeUserData(Keys.attachedDialect)
            val metadata = guessedDialect?.connectionContextExtractor?.gatherContext(project)
            inferredDatabase = metadata?.database
        }
    }

    private fun ensureToolbarIsVisibleIfNecessary() {
        val editor = this.getCurrentEditor() ?: return
        guessDialect()?.let {
            ensureSetupToolbarRequirements()
            (editor as? EditorEx)?.permanentHeaderComponent = toolbar
            editor.headerComponent = toolbar
        } ?: run {
            (editor as? EditorEx)?.permanentHeaderComponent = null
            editor.headerComponent = null
        }
    }

    private fun ensureSetupToolbarRequirements() {
        val requirements = guessDialect()?.connectionContextExtractor?.requirements() ?: emptySet()
        if (requirements.contains(ConnectionContextRequirement.DATABASE)) {
            toolbar.showDatabaseSelector()
        } else {
            toolbar.hideDatabaseSelector()
        }
    }

    private fun analyzeFileFromScratch() {
        val editor = this.getCurrentEditor() ?: return
        runCatching {
            val psiFile = PsiManager.getInstance(editor.project!!).findFile(editor.virtualFile) ?: return
            DaemonCodeAnalyzer.getInstance(editor.project).restart(psiFile)
        }
    }

    private fun getCurrentEditor(): Editor? {
        val selectedEditor = FileEditorManager.getInstance(project).selectedEditor
        return (selectedEditor as? TextEditor)?.editor
    }

    private fun setupToolbarForEditor() {
        guessAndStoreDialect()
        ensureToolbarIsVisibleIfNecessary()
        toolbar.reloadDatabases()
    }

    private fun initializeToolbar() {
        if (!::toolbar.isInitialized) {
            toolbar = MdbJavaEditorToolbar(
                project,
                coroutineScope,
                onConnected = this::onConnected,
                onDisconnected = this::onDisconnected,
                onDatabaseSelected = this::onDatabaseSelected,
                onDatabaseUnselected = this::onDatabaseUnselected
            )
            val localDataSourceManager = DataSourceManager.byDataSource(project, LocalDataSource::class.java) ?: return
            toolbar.reloadDataSources(localDataSourceManager.dataSources)
        }
    }

    override suspend fun execute(project: Project) {
        this.project = project
        this.initializeToolbar()

        this.setupToolbarForEditor()

        val messageBusConnection = project.messageBus.connect()
        messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
        messageBusConnection.subscribe(DataSourceManager.TOPIC, this)
        messageBusConnection.subscribe(JdbcDriverManager.TOPIC, this)
        messageBusConnection.subscribe(PsiModificationTracker.TOPIC, this)
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        this.setupToolbarForEditor()
    }

    override fun <T : RawDataSource?> dataSourceAdded(
        manager: DataSourceManager<T>,
        dataSource: T & Any,
    ) {
        val localDataSourceManager = manager as? LocalDataSourceManager ?: return
        toolbar.reloadDataSources(localDataSourceManager.dataSources)
    }

    override fun <T : RawDataSource?> dataSourceRemoved(
        manager: DataSourceManager<T>,
        dataSource: T & Any,
    ) {
        val localDataSourceManager = manager as? LocalDataSourceManager ?: return
        toolbar.reloadDataSources(localDataSourceManager.dataSources)
    }

    override fun <T : RawDataSource?> dataSourceChanged(
        manager: DataSourceManager<T>?,
        dataSource: T?,
    ) {
        val localDataSourceManager = manager as? LocalDataSourceManager ?: return
        toolbar.reloadDataSources(localDataSourceManager.dataSources)
    }

    override fun onTerminated(
        dataSource: LocalDataSource,
        configuration: ConsoleRunConfiguration?,
    ) {
        toolbar.disconnect(dataSource)
    }

    override fun modificationCountChanged() {
        this.getCurrentEditor()?.let {
            guessedDialects.remove(it)
            ensureToolbarIsVisibleIfNecessary()
        }
    }

    companion object {
        fun getToolbarFromEditor(editor: Editor) = editor.headerComponent as? MdbJavaEditorToolbar
    }
}
