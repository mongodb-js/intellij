package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.dataSource.connection.ConnectionRequestor
import com.intellij.database.model.RawDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.database.run.ConsoleRunConfiguration
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.rd.util.launchChildOnUi
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.util.messages.MessageBusConnection
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isMongoDbDataSource
import kotlinx.coroutines.*

/**
 * This decorator listens to an IntelliJ Editor lifecycle
 * and attaches our toolbar if necessary.
 *
 * @param coroutineScope
 */
class EditorToolbarDecorator(
    private val coroutineScope: CoroutineScope,
) : EditorFactoryListener,
    DataSourceManager.Listener,
    DatabaseConnectionManager.Listener {
    /**
     * This needs to be synchronised with the MongoDbVirtualFileDataSourceProvider field with the same name.
     *
     * @see MongoDbVirtualFileDataSourceProvider
     */
    internal val attachedDataSource: Key<LocalDataSource> = Key.create("com.mongodb.jbplugin.AttachedDataSource")
    internal val toolbar =
        MdbJavaEditorToolbar(
            onDataSourceSelected = this::onDataSourceSelected,
            onDataSourceUnselected = this::onDataSourceUnselected,
        )
    internal lateinit var editor: Editor
    internal lateinit var messageBusConnection: MessageBusConnection

    fun onDataSourceSelected(dataSource: LocalDataSource) {
        editor.putUserData(attachedDataSource, dataSource)
        val project = editor.project ?: return

        if (!dataSource.isConnected()) {
            coroutineScope.launch {
                toolbar.connecting = true
                val connectionManager = DatabaseConnectionManager.getInstance()
                val connectionHandler =
                    connectionManager
                        .build(project, dataSource)
                        .setRequestor(ConnectionRequestor.Anonymous())
                        .setAskPassword(true)
                        .setRunConfiguration(
                            ConsoleRunConfiguration.newConfiguration(project).apply {
                                setOptionsFromDataSource(dataSource)
                            },
                        )

                val connection =
                    async {
                        val loadingAnimation =
                            launchChildOnUi {
                                // keep updating the UI for the loading spinner
                                while (true) {
                                    delay(50)
                                    toolbar.updateUI()
                                }
                            }
                        val connectionJob = runCatching { connectionHandler.create()?.get() }
                        connectionJob.onFailure {
                            toolbar.connecting = false
                            toolbar.failedConnection = dataSource
                        }

                        loadingAnimation.cancelAndJoin()
                        connectionJob.getOrNull()
                    }.await()

                toolbar.failedConnection?.let {
                    return@launch
                }

                toolbar.connecting = false
                toolbar.updateUI()

                // could not connect, do nothing
                if (connection == null || !dataSource.isConnected()) {
                    toolbar.selectedDataSource = null // remove data source because we didn't connect
                    return@launch
                }

                editor.virtualFile?.putUserData(attachedDataSource, dataSource)
            }
        } else {
            editor.virtualFile?.putUserData(attachedDataSource, dataSource)
        }
    }

    fun onDataSourceUnselected() {
        editor.virtualFile?.removeUserData(attachedDataSource)
    }

    override fun editorCreated(event: EditorFactoryEvent) {
        editor = event.editor

        editor.project?.let {
            val project = editor.project!!
            messageBusConnection = project.messageBus.connect()
            messageBusConnection.subscribe(DataSourceManager.TOPIC, this)
            messageBusConnection.subscribe(DatabaseConnectionManager.TOPIC, this)
            val localDataSourceManager = DataSourceManager.byDataSource(project, LocalDataSource::class.java) ?: return
            toolbar.dataSources = localDataSourceManager.dataSources.filter { it.isMongoDbDataSource() }
        }

        ensureToolbarIsVisibleIfNecessary()
    }

    override fun editorReleased(event: EditorFactoryEvent) {
    }

    private fun ensureToolbarIsVisibleIfNecessary() {
        if (!editor.hasHeaderComponent()) {
            if (isEditingJavaFileWithMongoDbRelatedCode()) {
                (editor as EditorEx?)?.permanentHeaderComponent = toolbar
                editor.headerComponent = toolbar
            }
        } else {
            if (!isEditingJavaFileWithMongoDbRelatedCode()) {
                (editor as EditorEx?)?.permanentHeaderComponent = null
                editor.headerComponent = null
            }
        }
    }

    private fun isEditingJavaFileWithMongoDbRelatedCode(): Boolean {
        val project = editor.project ?: return false
        val psiFile = PsiManager.getInstance(project).findFile(editor.virtualFile) ?: return false
        if (psiFile.language != JavaLanguage.INSTANCE) {
            return false
        }

        val javaPsiFile = psiFile as PsiJavaFile
        return arrayOf(
            this::isUsingTheJavaDriver,
        ).any { it(javaPsiFile) }
    }

    private fun isUsingTheJavaDriver(psiFile: PsiJavaFile): Boolean {
        val importStatements = psiFile.importList?.allImportStatements ?: emptyArray()
        return importStatements.any {
            return@any it.importReference?.canonicalText?.startsWith("com.mongodb") == true
        }
    }

    override fun <T : RawDataSource?> dataSourceAdded(
        manager: DataSourceManager<T>,
        dataSource: T & Any,
    ) {
        val localDataSourceManager = manager as? LocalDataSourceManager ?: return
        toolbar.dataSources = localDataSourceManager.dataSources.filter { it.isMongoDbDataSource() }
    }

    override fun <T : RawDataSource?> dataSourceRemoved(
        manager: DataSourceManager<T>,
        dataSource: T & Any,
    ) {
        val localDataSourceManager = manager as? LocalDataSourceManager ?: return

        if (toolbar.selectedDataSource?.uniqueId == dataSource.uniqueId) {
            toolbar.selectedDataSource = null
        }

        toolbar.dataSources = localDataSourceManager.dataSources.filter { it.isMongoDbDataSource() }
    }

    override fun <T : RawDataSource?> dataSourceChanged(
        manager: DataSourceManager<T>?,
        dataSource: T?,
    ) {
        val localDataSourceManager = manager as? LocalDataSourceManager ?: return

        if (toolbar.selectedDataSource?.uniqueId == dataSource?.uniqueId) {
            toolbar.selectedDataSource = null
        }

        toolbar.dataSources = localDataSourceManager.dataSources.filter { it.isMongoDbDataSource() }
    }

    override fun connectionChanged(
        connection: DatabaseConnection,
        added: Boolean,
    ) {
        val dataSource = connection.connectionPoint.dataSource
        val selectedDataSource = toolbar.selectedDataSource

        if (dataSource.isMongoDbDataSource() &&
            !dataSource.isConnected() &&
            selectedDataSource?.uniqueId == dataSource.uniqueId
        ) {
            toolbar.selectedDataSource = null
        }
    }
}
