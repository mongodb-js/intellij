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
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.removeUserData
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.messages.MessageBusConnection
import com.mongodb.jbplugin.dialects.ConnectionMetadataRequirement
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.editor.MongoDbVirtualFileDataSourceProvider.Keys
import com.mongodb.jbplugin.observability.probe.NewConnectionActivatedProbe
import kotlinx.coroutines.CoroutineScope

private val log = logger<EditorToolbarDecorator>()

private val ALL_DIALECTS = listOf(
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
) : EditorFactoryListener,
    FileEditorManagerListener,
    DataSourceManager.Listener,
    JdbcDriverManager.Listener,
    PsiModificationTracker.Listener {
    internal lateinit var toolbar: MdbJavaEditorToolbar
    internal lateinit var editor: Editor
    internal lateinit var messageBusConnection: MessageBusConnection
    internal var inferredDatabase: String? = null
    internal var guessedDialect: Dialect<PsiElement, Project>? = null

    fun onConnected(dataSource: LocalDataSource) {
        editor.virtualFile?.putUserData(Keys.attachedDataSource, dataSource)
        editor.virtualFile?.removeUserData(Keys.attachedDatabase)

        ApplicationManager.getApplication().invokeLater {
            val project = editor.project!!
            val session = DatabaseSessionManager.openSession(project, dataSource, null)
            val probe = NewConnectionActivatedProbe()
            probe.connected(session)

            if (inferredDatabase != null) {
                toolbar.databaseComboBox.selectedDatabase = inferredDatabase
            }

            analyzeFileFromScratch()
        }
    }

    fun onDisconnected() {
        editor.virtualFile?.removeUserData(Keys.attachedDataSource)
        editor.virtualFile?.removeUserData(Keys.attachedDatabase)
        analyzeFileFromScratch()
    }

    fun onDatabaseSelected(database: String) {
        editor.virtualFile?.putUserData(Keys.attachedDatabase, database)
        analyzeFileFromScratch()
    }

    fun onDatabaseUnselected() {
        editor.virtualFile?.removeUserData(Keys.attachedDatabase)
        analyzeFileFromScratch()
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        (event.newEditor as? TextEditor)?.editor?.let {
            editor = it
            ensureToolbarIsVisibleIfNecessary()
            toolbar.reloadDatabases()
        }
    }

    override fun editorCreated(event: EditorFactoryEvent) {
        editor = event.editor

        editor.project?.let { project ->
            toolbar = MdbJavaEditorToolbar(
                project,
                coroutineScope,
                onConnected = this::onConnected,
                onDisconnected = this::onDisconnected,
                onDatabaseSelected = this::onDatabaseSelected,
                onDatabaseUnselected = this::onDatabaseUnselected
            )

            messageBusConnection = project.messageBus.connect()
            messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
            messageBusConnection.subscribe(DataSourceManager.TOPIC, this)
            messageBusConnection.subscribe(JdbcDriverManager.TOPIC, this)

            messageBusConnection.subscribe(PsiModificationTracker.TOPIC, this)
            val localDataSourceManager = DataSourceManager.byDataSource(project, LocalDataSource::class.java) ?: return
            toolbar.reloadDataSources(localDataSourceManager.dataSources)

            guessedDialect = guessDialect()
            if (guessedDialect != null) {
                editor.virtualFile?.putUserData(Keys.attachedDialect, guessedDialect)
            } else {
                editor.virtualFile?.removeUserData(Keys.attachedDialect)
            }
            val metadata = guessedDialect?.connectionContextExtractor?.gatherContext(project)
            inferredDatabase = metadata?.database

            ensureToolbarIsVisibleIfNecessary()
        }
    }

    override fun editorReleased(event: EditorFactoryEvent) {

    }

    private fun ensureToolbarIsVisibleIfNecessary() {
        if (guessedDialect != null) {
            ensureSetupToolbarRequirements()
            (editor as EditorEx?)?.permanentHeaderComponent = toolbar
            editor.headerComponent = toolbar
        } else {
            (editor as EditorEx?)?.permanentHeaderComponent = null
            editor.headerComponent = null
        }
    }

    private fun ensureSetupToolbarRequirements() {
        if (guessedDialect == null) {
            return
        }

        val requirements = guessedDialect?.connectionContextExtractor?.requirements() ?: emptySet()
        if (requirements.contains(ConnectionMetadataRequirement.DATABASE)) {
            toolbar.showDatabaseSelector()
        } else {
            toolbar.hideDatabaseSelector()
        }
    }

    private fun guessDialect(): Dialect<PsiElement, Project>? {
        val project = editor.project ?: return null
        val psiFileResult = runCatching { PsiManager.getInstance(project).findFile(editor.virtualFile) }

        if (psiFileResult.isFailure) {
            return null
        }

        psiFileResult.onFailure {
            log.info("Could not find virtual file for current editor", it)
        }

        val psiFile = psiFileResult.getOrNull() ?: return null
        return ALL_DIALECTS.find { it.isUsableForSource(psiFile) }
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
        ensureToolbarIsVisibleIfNecessary()
    }

    private fun analyzeFileFromScratch() {
        runCatching {
            val psiFile = PsiManager.getInstance(editor.project!!).findFile(editor.virtualFile) ?: return
            DaemonCodeAnalyzer.getInstance(editor.project).restart(psiFile)
        }
    }

    companion object {
        fun getToolbarFromEditor(editor: Editor) = editor.headerComponent as? MdbJavaEditorToolbar
    }
}
