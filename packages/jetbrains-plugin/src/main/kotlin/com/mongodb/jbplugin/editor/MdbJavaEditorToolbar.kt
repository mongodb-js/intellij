/**
 * MdbJavaEditorToolbar: This file contains the toolbar class itself responsible for rendering and interacting with
 * the toolbar and also a data class to encapsulate the current state of Toolbar itself
 */

package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.editor.inputs.DataSourceComboBox
import com.mongodb.jbplugin.editor.inputs.DatabaseComboBox
import com.mongodb.jbplugin.editor.models.getToolbarModel
import com.mongodb.jbplugin.editor.services.implementations.getEditorService
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.i18n.Icons.scaledToText
import com.mongodb.jbplugin.i18n.MdbToolbarMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.*

/**
 * Toolbar class that encapsulates rendering and interacting with the toolbar itself
 *
 * @param dataSourceModel
 * @param databaseModel
 */
class MdbJavaEditorToolbar(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
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
        DataSourceComboBox(
            parent = dropdownsPanel,
            project = project,
            coroutineScope = coroutineScope,
        )
    }

    private val databaseComboBox: DatabaseComboBox by lazy {
        DatabaseComboBox(
            parent = dropdownsPanel,
            project = project,
            coroutineScope = coroutineScope,
        )
    }

    init {
        // Setup UI
        dropdownsPanel.layout = BoxLayout(dropdownsPanel, BoxLayout.X_AXIS)
        mainPanel.add(dropdownsPanel, BorderLayout.EAST)
    }

    private fun showDatabasesComboBox() {
        if (!databaseComboBoxVisible) {
            databaseComboBoxVisible = true
            databaseComboBox.attachToParent()
        }
    }

    private fun hideDatabasesComboBox() {
        if (databaseComboBoxVisible) {
            databaseComboBoxVisible = false
            databaseComboBox.removeFromParent()
        }
    }

    fun attachToEditor(editor: Editor, showDatabaseComboBox: Boolean) {
        project.getToolbarModel().loadInitialData()
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
        project.getToolbarModel().loadInitialData()
        dataSourceComboBox.attachToParent()
        if (showDatabaseComboBox) {
            showDatabasesComboBox()
        } else {
            hideDatabasesComboBox()
        }
        parent.add(dropdownsPanel)
    }

    // Subclassing the JBPanel here mostly to make testing easier
    private class MdbJavaEditorToolbarPanel : JBPanel<Nothing>(BorderLayout())

    companion object {
        suspend fun showModalForSelection(
            project: Project,
            coroutineScope: CoroutineScope,
            okButtonText: String = "OK",
        ): LocalDataSource? {
            val editorService = project.getEditorService()
            return withContext(Dispatchers.Main) {
                val localToolbar =
                    MdbJavaEditorToolbar(
                        project = project,
                        coroutineScope = coroutineScope,
                    )

                val dialog = SelectConnectionDialogWrapper(
                    MdbToolbarMessages.message("connection.chooser.popup.information.message"),
                    project,
                    coroutineScope,
                    localToolbar,
                    editorService.isDatabaseComboBoxVisibleForSelectedEditor(),
                    okButtonText,
                )

                val toolbarModel = project.getToolbarModel()
                val oldToolbarState = toolbarModel.toolbarState.value
                if (!dialog.showAndGet()) {
                    oldToolbarState.selectedDataSource?.let { toolbarModel.selectDataSource(it) }
                    oldToolbarState.selectedDatabase?.let { toolbarModel.selectDatabase(it) }
                    return@withContext null
                } else {
                    return@withContext toolbarModel.toolbarState.value.selectedDataSource
                }
            }
        }

        /**
         * @param project
         * @param parentScope
         * @param toolbar
         * @param databaseComboBoxVisible
         * @property informationMessage
         */
        class SelectConnectionDialogWrapper(
            @Nls val informationMessage: String,
            private val project: Project,
            private val parentScope: CoroutineScope,
            private val toolbar: MdbJavaEditorToolbar,
            private val databaseComboBoxVisible: Boolean,
            private val okButtonText: String,
        ) : DialogWrapper(project, false) {
            private val dialogScope = parentScope.childScope()
            private lateinit var okAction: Action
            private lateinit var cancelAction: Action

            init {
                init()
                val toolbarModel = project.getToolbarModel()
                okAction.isEnabled =
                    toolbarModel.toolbarState.value.selectedDataSource?.isConnected() == true
                observeToolbarState()
            }

            override fun createActions(): Array<Action> {
                okAction = object : DialogWrapperAction(okButtonText) {
                    override fun doAction(e: ActionEvent?) {
                        val toolbarModel = project.getToolbarModel()
                        if (
                            toolbarModel.toolbarState.value.selectedDataSource?.isConnected() ==
                            true
                        ) {
                            close(OK_EXIT_CODE)
                        }
                    }
                }

                cancelAction = object : DialogWrapperAction("Cancel") {
                    override fun doAction(e: ActionEvent?) {
                        close(CANCEL_EXIT_CODE)
                    }
                }

                return arrayOf(okAction, cancelAction)
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

            override fun dispose() {
                super.dispose()
                dialogScope.cancel()
            }

            private fun observeToolbarState() {
                dialogScope.launch {
                    project.getToolbarModel().toolbarState.collect { state ->
                        okAction.isEnabled = state.selectedDataSource?.isConnected() == true
                    }
                }
            }
        }
    }
}
