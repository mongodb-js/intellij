package com.mongodb.jbplugin.editor

import com.intellij.database.console.JdbcDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.model.RawDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.database.run.ConsoleRunConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.util.PsiModificationTracker
import com.mongodb.jbplugin.editor.models.implementations.ProjectDataSourceModel
import com.mongodb.jbplugin.editor.models.implementations.ProjectDatabaseModel
import com.mongodb.jbplugin.editor.services.MdbPluginDisposable
import com.mongodb.jbplugin.editor.services.implementations.getDataSourceService
import com.mongodb.jbplugin.editor.services.implementations.getEditorService
import com.mongodb.jbplugin.editor.services.implementations.useToolbarSettings
import io.ktor.util.collections.*
import org.jetbrains.annotations.TestOnly

import kotlinx.coroutines.CoroutineScope

private val log = logger<EditorToolbarDecorator>()

/**
 * @param coroutineScope
 */
class EditorToolbarDecorator(
    private val coroutineScope: CoroutineScope,
) : ProjectActivity,
    FileEditorManagerListener,
    PsiModificationTracker.Listener,
    DataSourceManager.Listener,
    JdbcDriverManager.Listener {
    // These variables are lateinit because we initialise them when the project activity starts using execute method
    // below. We need to keep a hold of them because other listeners also use them in some way
    private var project: Project? = null
    private var toolbar: MdbJavaEditorToolbar? = null

    // Internal only because we spy on the method in tests
    internal fun setupSubscriptionsForProject(project: Project) {
        // Registering messageBus.connect to our MdbPluginDisposable ensures that the subscriptions are cleared
        // when either the plugin unloads or the project is closed
        val messageBusConnection = project.messageBus.connect(MdbPluginDisposable.getInstance(project))
        messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
        messageBusConnection.subscribe(PsiModificationTracker.TOPIC, this)
        messageBusConnection.subscribe(DataSourceManager.TOPIC, this)
        messageBusConnection.subscribe(JdbcDriverManager.TOPIC, this)
    }

    @TestOnly
    internal fun getToolbarForTests(): MdbJavaEditorToolbar? = toolbar

    // execute can get called multiple times during an IntelliJ session which is why
    // we override the stored project and toolbar everytime it is called
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().runReadAction {
            val toolbarSettings = useToolbarSettings()
            val editorService = getEditorService(project)
            val dataSourceService = getDataSourceService(project)

            val dataSourceModel = ProjectDataSourceModel(
                toolbarSettings = toolbarSettings,
                dataSourceService = dataSourceService,
                editorService = editorService
            )
            val databaseModel = ProjectDatabaseModel(
                toolbarSettings = toolbarSettings,
                dataSourceService = dataSourceService,
                editorService = editorService
            )

            this.project = project
            this.setupSubscriptionsForProject(project)

            this.toolbar = MdbJavaEditorToolbar(
                dataSourceModel = dataSourceModel,
                databaseModel = databaseModel,
            )

            editorService.toggleToolbarForSelectedEditor(this.toolbar!!, false)
        }
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val editorService = project?.let {
            if (it.isDisposed) {
                null
            } else {
                getEditorService(it)
            }
        }

        toolbar?.let {
            editorService?.toggleToolbarForSelectedEditor(it, true)
        }
    }

    override fun modificationCountChanged() {
        val editorService = project?.let {
            if (it.isDisposed) {
                null
            } else {
                getEditorService(it)
            }
        }
        editorService?.removeDialectForSelectedEditor()

        toolbar?.let {
            editorService?.toggleToolbarForSelectedEditor(it, true)
        }
    }

    override fun <T : RawDataSource?> dataSourceAdded(manager: DataSourceManager<T>, dataSource: T & Any) {
        // An added DataSource can't possibly change the selection state hence just reloading the DataSources
        toolbar?.reloadDataSources()
    }

    override fun <T : RawDataSource?> dataSourceRemoved(manager: DataSourceManager<T>, dataSource: T & Any) {
        // A removed DataSource might be our selected one so we first remove it and then reload the DataSources
        // Unselection when happened is expected to trigger state change listener so that will update
        // also the attached resources to the selected editor and the stored DataSource in ToolbarSettings
        toolbar?.unselectDataSource(dataSource as LocalDataSource)
        toolbar?.reloadDataSources()
    }

    override fun <T : RawDataSource?> dataSourceChanged(manager: DataSourceManager<T>?, dataSource: T?) {
        toolbar?.reloadDataSources()
    }

    override fun onTerminated(dataSource: LocalDataSource, configuration: ConsoleRunConfiguration?) {
        toolbar?.unselectDataSource(dataSource)
    }
}
