package com.mongodb.jbplugin.editor

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.database.console.JdbcDriverManager
import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.model.RawDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.database.run.ConsoleRunConfiguration
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.removeUserData
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.messages.MessageBusConnection
import com.mongodb.jbplugin.editor.MongoDbVirtualFileDataSourceProvider.Keys
import com.mongodb.jbplugin.observability.probe.NewConnectionActivatedProbe
import kotlinx.coroutines.CoroutineScope

private val log = logger<EditorToolbarDecorator>()

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

    fun onConnected(dataSource: LocalDataSource) {
        editor.virtualFile?.putUserData(Keys.attachedDataSource, dataSource)
        editor.virtualFile?.removeUserData(Keys.attachedDatabase)

        ApplicationManager.getApplication().invokeLater {
            val project = editor.project!!
            val session = DatabaseSessionManager.openSession(project, dataSource, null)
            val probe = NewConnectionActivatedProbe()
            probe.connected(session)

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

        toolbar = MdbJavaEditorToolbar(
            editor.project!!,
            coroutineScope,
            onConnected = this::onConnected,
            onDisconnected = this::onDisconnected,
            onDatabaseSelected = this::onDatabaseSelected,
            onDatabaseUnselected = this::onDatabaseUnselected
        )

        editor.project?.let { project ->
            messageBusConnection = project.messageBus.connect()
            messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
            messageBusConnection.subscribe(DataSourceManager.TOPIC, this)
            messageBusConnection.subscribe(JdbcDriverManager.TOPIC, this)

            messageBusConnection.subscribe(PsiModificationTracker.TOPIC, this)
            val localDataSourceManager = DataSourceManager.byDataSource(project, LocalDataSource::class.java) ?: return
            toolbar.reloadDataSources(localDataSourceManager.dataSources)
        }

        ensureToolbarIsVisibleIfNecessary()
    }

    override fun editorReleased(event: EditorFactoryEvent) {

    }

    private fun ensureToolbarIsVisibleIfNecessary() {
        if (!editor.hasHeaderComponent()) {
            if (isToolbarSetUpForCurrentFile()) {
                (editor as EditorEx?)?.permanentHeaderComponent = toolbar
                editor.headerComponent = toolbar
            }
        } else {
            if (!isToolbarSetUpForCurrentFile()) {
                (editor as EditorEx?)?.permanentHeaderComponent = null
                editor.headerComponent = null
            }
        }
    }

    private fun isToolbarSetUpForCurrentFile(): Boolean {
        val project = editor.project ?: return false
        val psiFileResult = runCatching { PsiManager.getInstance(project).findFile(editor.virtualFile) }

        if (psiFileResult.isFailure) {
            return false
        }

        psiFileResult.onFailure {
            log.info("Could not find virtual file for current editor", it)
        }

        val psiFile = psiFileResult.getOrNull() ?: return false

        if (psiFile.language != JavaLanguage.INSTANCE) {
            return false
        }

        val javaPsiFile = psiFile as PsiJavaFile
        if (isUsingSpringDataMongoDb(javaPsiFile)) {
            toolbar.showDatabaseSelector()
            return true
        }

        if (isUsingTheJavaDriver(javaPsiFile)) {
            toolbar.hideDatabaseSelector()
            return true
        }

        return false
    }

    private fun isUsingTheJavaDriver(psiFile: PsiJavaFile): Boolean {
        val importStatements = psiFile.importList?.allImportStatements ?: emptyArray()
        return importStatements.any {
            return@any it.importReference?.canonicalText?.startsWith("com.mongodb") == true
        }
    }

    private fun isUsingSpringDataMongoDb(psiFile: PsiJavaFile): Boolean {
        val importStatements = psiFile.importList?.allImportStatements ?: emptyArray()
        return importStatements.any {
            return@any it.importReference?.canonicalText?.startsWith("org.springframework.data.mongodb") == true
        }
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
