/**
 * MdbJavaEditorToolbar: This file contains the toolbar class itself responsible for rendering and interacting with
 * the toolbar and also a data class to encapsulate the current state of Toolbar itself
 */

package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.mongodb.jbplugin.editor.inputs.DataSourceComboBox
import com.mongodb.jbplugin.editor.inputs.DatabaseComboBox
import com.mongodb.jbplugin.editor.models.DataSourceModel
import com.mongodb.jbplugin.editor.models.DatabaseModel
import com.mongodb.jbplugin.editor.models.implementations.ProjectDataSourceModel
import com.mongodb.jbplugin.editor.models.implementations.ProjectDatabaseModel
import com.mongodb.jbplugin.editor.services.ConnectionState
import com.mongodb.jbplugin.editor.services.implementations.InMemoryToolbarSettings
import com.mongodb.jbplugin.editor.services.implementations.getDataSourceService
import com.mongodb.jbplugin.editor.services.implementations.getEditorService
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.i18n.Icons.scaledToText
import com.mongodb.jbplugin.i18n.MdbToolbarMessages
import com.mongodb.jbplugin.observability.useLogMessage
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.*

private val log = logger<MdbJavaEditorToolbar>()

/**
 * Data class to encapsulate the current state of the Toolbar
 *
 * @property dataSources
 * @property selectedDataSource
 * @property databases
 * @property selectedDatabase
 */
data class ToolbarState(
    val dataSources: List<LocalDataSource>,
    val selectedDataSource: LocalDataSource?,
    val databases: List<String>,
    val selectedDatabase: String?,
)

/**
 * Toolbar class that encapsulates rendering and interacting with the toolbar itself
 *
 * @param dataSourceModel
 * @param databaseModel
 */
class MdbJavaEditorToolbar(
    private val dataSourceModel: DataSourceModel,
    private val databaseModel: DatabaseModel,
) {
    // The entire panel that spans from left to the right and gets attached to the editor itself
    private val mainPanel = MdbJavaEditorToolbarPanel()

    // The panel that houses our dropdowns and gets attached to the mainPanel above
    private val dropdownsPanel = JPanel()

    // A value which we use only to decide whether to list databases on selection of a DataSource
    // Set when the toolbar is rendered for an editor
    private var databaseComboBoxVisible = false

    // Initializing DataSourceComboBox lazily because it also kickstart a connection if a DataSource is to be
    // pre-selected which is why it is better to do it only when we start showing the toolbar in attachToEditor
    private val dataSourceComboBox: DataSourceComboBox by lazy {
        val (selectedDataSource, dataSources) = dataSourceModel.loadComboBoxState()
        DataSourceComboBox(
            parent = dropdownsPanel,
            onDataSourceSelected = ::onDataSourceSelected,
            onDataSourceUnselected = ::onDataSourceUnselected,
            initialDataSources = dataSources,
            initialSelectedDataSource = selectedDataSource
        )
    }
    private val databaseComboBox: DatabaseComboBox = DatabaseComboBox(
        parent = dropdownsPanel,
        onDatabaseSelected = databaseModel::onDatabaseSelected,
        onDatabaseUnselected = databaseModel::onDatabaseUnselected,
    )

    init {
        // Setup UI
        dropdownsPanel.layout = BoxLayout(dropdownsPanel, BoxLayout.X_AXIS)
        mainPanel.add(dropdownsPanel, BorderLayout.EAST)
    }

    // Listener for when a DataSource is selected
    private fun onDataSourceSelected(dataSource: LocalDataSource) {
        dataSourceModel.onDataSourceSelected(dataSource) {
            if (it is ConnectionState.ConnectionSuccess && databaseComboBoxVisible) {
                databaseModel.loadComboBoxState(
                    selectedDataSource = dataSource,
                    onComboBoxLoadingStateChanged = databaseComboBox::onLoadingStateChanged
                )
            }
            dataSourceComboBox.connectionStateChanged(it)
        }
    }

    // Listener for when a DataSource is unselected
    private fun onDataSourceUnselected(dataSource: LocalDataSource) {
        if (dataSourceModel.getStoredDataSource()?.uniqueId == dataSource.uniqueId) {
            dataSourceModel.onDataSourceUnselected(dataSource)
            // We unselect the database to trigger a state change via model
            databaseComboBox.selectedDatabase?.let {
                databaseComboBox.unselectDatabase(it)
            }
            databaseComboBox.setComboBoxState(emptyList(), null)
        }
    }

    private fun showDatabasesComboBox() {
        if (!databaseComboBoxVisible) {
            databaseComboBoxVisible = true
            databaseComboBox.attachToParent()
            dataSourceComboBox.selectedDataSource?.let {
                databaseModel.loadComboBoxState(
                    selectedDataSource = it,
                    onComboBoxLoadingStateChanged = databaseComboBox::onLoadingStateChanged
                )
            }
        }
    }

    private fun hideDatabasesComboBox() {
        if (databaseComboBoxVisible) {
            databaseComboBoxVisible = false
            databaseComboBox.removeFromParent()
        }
    }

    fun attachToEditor(editor: Editor, showDatabaseComboBox: Boolean) {
        dataSourceComboBox.attachToParent()
        if (showDatabaseComboBox) {
            showDatabasesComboBox()
        } else {
            hideDatabasesComboBox()
        }
        (editor as? EditorEx)?.permanentHeaderComponent = mainPanel
        editor.headerComponent = mainPanel
    }

    fun detachFromEditor(editor: Editor) {
        if (editor.headerComponent is MdbJavaEditorToolbarPanel) {
            (editor as? EditorEx)?.permanentHeaderComponent = null
            editor.headerComponent = null
        }
    }

    fun attachToParent(parent: JComponent, showDatabaseComboBox: Boolean) {
        dataSourceComboBox.attachToParent()
        if (showDatabaseComboBox) {
            showDatabasesComboBox()
        } else {
            hideDatabasesComboBox()
        }
        parent.add(dropdownsPanel)
    }

    fun reloadDataSources() {
        // Setting the combobox state does not trigger the state changed listener which is exactly
        // what we want here as we're not really changing the selection, just reloading the DataSources
        dataSourceComboBox.setComboBoxState(
            dataSourceModel.listDataSources(),
            dataSourceComboBox.selectedDataSource,
        )
    }

    fun unselectDataSource(dataSource: LocalDataSource) {
        dataSourceComboBox.unselectDataSource(dataSource)
    }

    fun getToolbarState(): ToolbarState = ToolbarState(
        dataSources = dataSourceComboBox.dataSources,
        selectedDataSource = dataSourceComboBox.selectedDataSource,
        databases = databaseComboBox.databases,
        selectedDatabase = databaseComboBox.selectedDatabase
    )

    fun setToolbarState(state: ToolbarState) {
        dataSourceComboBox.setComboBoxState(state.dataSources, state.selectedDataSource)
        // Setting the combobox state above does not trigger the state changed listener
        // Which is why we manually select the data source on the actual model
        state.selectedDataSource?.let {
            dataSourceModel.onDataSourceSelected(state.selectedDataSource) {
                // We purposely ignore the callback calls here because we already have our state set
            }
        }

        // Same for databaseComboBox
        databaseComboBox.setComboBoxState(state.databases, state.selectedDatabase)
        state.selectedDatabase?.let {
            databaseModel.onDatabaseSelected(state.selectedDatabase)
        }
    }

    // Subclassing the JBPanel here mostly to make testing easier
    private class MdbJavaEditorToolbarPanel : JBPanel<Nothing>(BorderLayout())

    companion object {
        fun showModalForSelection(
            project: Project,
            onConnectionStateChange: (
                status: ConnectionState,
                dataSource: LocalDataSource
            ) -> Unit = { _, _ -> }
        ) {
            val editorService = getEditorService(project)
            val toolbar = editorService.getToolbarFromSelectedEditor()
            toolbar ?: run {
                log.warn(
                    useLogMessage(
                        "Could not show modal for selection, toolbar on attached editor is null"
                    ).build()
                )
                return
            }

            ApplicationManager.getApplication().invokeLater {
                val (_, selectedDataSource, _, selectedDatabase) = toolbar.getToolbarState()

                val dataSourceService = getDataSourceService(project)
                val toolbarSettings = InMemoryToolbarSettings(
                    selectedDataSource?.uniqueId,
                    selectedDatabase
                )
                val dataSourceModel = ProjectDataSourceModel(
                    toolbarSettings = toolbarSettings,
                    editorService = editorService,
                    dataSourceService = dataSourceService,
                )
                val databaseModel = ProjectDatabaseModel(
                    toolbarSettings = toolbarSettings,
                    editorService = editorService,
                    dataSourceService = dataSourceService,
                )

                val localToolbar =
                    MdbJavaEditorToolbar(
                        dataSourceModel = dataSourceModel,
                        databaseModel = databaseModel
                    )

                val dialog = SelectConnectionDialogWrapper(
                    MdbToolbarMessages.message("connection.chooser.popup.information.message"),
                    project,
                    localToolbar,
                    editorService.isDatabaseComboBoxVisibleForSelectedEditor(),
                )

                if (dialog.showAndGet()) {
                    val toolbarState = localToolbar.getToolbarState()

                    toolbar.setToolbarState(toolbarState)
                    dataSourceService.connect(
                        toolbarState.selectedDataSource!!
                    ) { connectionState ->
                        ApplicationManager.getApplication().invokeLater {
                            onConnectionStateChange(
                                connectionState,
                                toolbarState.selectedDataSource
                            )
                        }
                    }
                }
            }
        }

        /**
         * @param project
         * @param toolbar
         * @param databaseComboBoxVisible
         * @property informationMessage
         */
        private class SelectConnectionDialogWrapper(
            @Nls val informationMessage: String,
            project: Project,
            private val toolbar: MdbJavaEditorToolbar,
            private val databaseComboBoxVisible: Boolean
        ) : DialogWrapper(project, false) {
            init {
                init()
            }

            override fun createCenterPanel(): JComponent =
                JPanel(BorderLayout()).apply {
                    add(
                        JBLabel(
                            informationMessage,
                            Icons.information.scaledToText(),
                            SwingConstants.LEFT
                        ).apply {
                            border = JBUI.Borders.empty(10)
                        },
                        BorderLayout.NORTH
                    )
                    toolbar.attachToParent(this, databaseComboBoxVisible)
                    (peer.window as? JDialog)?.isUndecorated = true
                }
        }
    }
}
