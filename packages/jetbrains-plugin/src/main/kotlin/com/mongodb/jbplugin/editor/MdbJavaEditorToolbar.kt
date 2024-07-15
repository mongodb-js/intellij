package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.asSequence
import com.intellij.sql.indexOf
import com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.jetbrains.rd.util.AtomicReference
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isMongoDbDataSource
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.i18n.Icons.scaledToText
import com.mongodb.jbplugin.i18n.MdbToolbarMessages
import java.awt.BorderLayout
import java.awt.event.ItemEvent.DESELECTED
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.SwingConstants

typealias DataSourceSelectedListener = (LocalDataSource) -> Unit
typealias DataSourceUnselectedListener = () -> Unit

/**
 * Represents the toolbar that will be inserted into an active Java editor.
 *
 * @param onDataSourceSelected
 * @param onDataSourceUnselected
 */
class MdbJavaEditorToolbar(
    private val onDataSourceSelected: DataSourceSelectedListener,
    private val onDataSourceUnselected: DataSourceUnselectedListener,
) : JBPanel<MdbJavaEditorToolbar>(BorderLayout()) {
    var connecting: Boolean = false
    var failedConnection: LocalDataSource? = null
    private val connectionComboBox =
        ComboBox(
            DefaultComboBoxModel(
                emptyArray<LocalDataSource>(),
            ),
        )

    var dataSources: List<LocalDataSource>
        set(value) {
            failedConnection = null
            connecting = false

            val selectedItem = this.selectedDataSource?.uniqueId
            val model = connectionComboBox.model as DefaultComboBoxModel<LocalDataSource>
            model.removeAllElements()
            model.addElement(null)
            model.addAll(value)
            selectDataSourceWithId(selectedItem)
        }
        get() =
            (connectionComboBox.model as DefaultComboBoxModel<LocalDataSource>).asSequence().toList().filterNotNull()

    var selectedDataSource: LocalDataSource?
        set(value) {
            failedConnection = null
            connecting = false

            value?.let {
                connectionComboBox.selectedItem = value
            } ?: run {
                connectionComboBox.selectedItem = null
            }
        }
        get() = connectionComboBox.selectedItem as? LocalDataSource

    init {
        add(connectionComboBox, BorderLayout.EAST)
        connectionComboBox.addItemListener {
            failedConnection = null
            connecting = false

            if (it.stateChange == DESELECTED) {
                onDataSourceUnselected()
            } else {
                onDataSourceSelected(it.item as LocalDataSource)
            }
        }
        connectionComboBox.putClientProperty(ANIMATION_IN_RENDERER_ALLOWED, true)
        connectionComboBox.setRenderer { _, value, index, _, _ ->
            if (value == null && index == -1) {
                JBLabel(
                    MdbToolbarMessages.message("attach.datasource.to.editor"),
                    Icons.logo.scaledToText(),
                    SwingConstants.LEFT,
                )
            } else {
                value?.let {
                    val icon =
                        if (value.isConnected()) {
                            Icons.logoConnected.scaledToText()
                        } else if (connecting) {
                            Icons.loading.scaledToText()
                        } else if (failedConnection?.uniqueId == value.uniqueId) {
                            Icons.connectionFailed.scaledToText()
                        } else {
                            Icons.logo.scaledToText()
                        }
                    JBLabel(value.name, icon, SwingConstants.LEFT)
                } ?: JBLabel(
                    MdbToolbarMessages.message("detach.datasource.from.editor"),
                    Icons.remove.scaledToText(),
                    SwingConstants.LEFT,
                )
            }
        }
    }

    private fun selectDataSourceWithId(id: String?) {
        id?.let {
            val dataSourceIndex =
                (connectionComboBox.model as DefaultComboBoxModel<LocalDataSource?>)
                    .asSequence()
                    .toList()
                    .indexOf { it?.uniqueId == id }
            if (dataSourceIndex == -1) {
                connectionComboBox.selectedItem = null
            } else {
                connectionComboBox.selectedIndex = dataSourceIndex
            }
        } ?: run {
            connectionComboBox.selectedItem = null
        }
    }

    companion object {
        fun showModalForSelection(editor: Editor) {
            val project = editor.project ?: return

            ApplicationManager.getApplication().invokeLater {
                val selectedDataSource = AtomicReference<LocalDataSource?>(null)

                val toolbar =
                    MdbJavaEditorToolbar({
                        selectedDataSource.getAndSet(it)
                    }, {
                        selectedDataSource.getAndSet(null)
                    })

                val localDataSourceManager =
                    DataSourceManager.byDataSource(project, LocalDataSource::class.java)
                        ?: return@invokeLater
                toolbar.dataSources = localDataSourceManager.dataSources.filter { it.isMongoDbDataSource() }

                val dialog = SelectConnectionDialogWrapper(project, toolbar)
                if (dialog.showAndGet()) {
                    EditorToolbarDecorator
                        .getToolbarFromEditor(editor)
                        ?.selectedDataSource = selectedDataSource.get()
                }
            }
        }

/**
 * @param project
 * @param toolbar
 */
internal class SelectConnectionDialogWrapper(
            project: Project,
            private val toolbar: MdbJavaEditorToolbar,
        ) : DialogWrapper(project, false) {
            init {
                init()
            }

            override fun createCenterPanel(): JComponent =
                JPanel(BorderLayout()).apply {
                    add(toolbar)
                    (peer.window as? JDialog)?.isUndecorated = true
                }
        }
}
}
