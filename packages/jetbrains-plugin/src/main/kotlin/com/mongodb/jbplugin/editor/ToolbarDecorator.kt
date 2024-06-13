package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.model.RawDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.MessageBusConnection
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultComboBoxModel
import javax.swing.JPanel

private class Toolbar(dataSources: List<LocalDataSource>) {
    val panel: JPanel = JPanel(BorderLayout())
    val connectionComboBox =
        ComboBox(
            DefaultComboBoxModel(
                dataSources.toTypedArray(),
            ),
        )

    init {
        panel.add(connectionComboBox, BorderLayout.EAST)
    }

    fun refreshDataSources(dataSources: List<LocalDataSource>) {
        val model = connectionComboBox.model as DefaultComboBoxModel<LocalDataSource>
        model.removeAllElements()
        model.addAll(dataSources)
    }

    fun isToolbar(component: Component) = panel == component
}

class ToolbarDecorator : EditorFactoryListener, DataSourceManager.Listener {
    private val toolbar = Toolbar(emptyList())
    private lateinit var connection: MessageBusConnection
    private lateinit var editor: Editor

    override fun editorCreated(event: EditorFactoryEvent) {
        editor = event.editor

        if (editor.project != null) {
            connection =
                editor.project!!
                    .messageBus
                    .connect()

            connection.subscribe(
                DataSourceManager.TOPIC,
                this,
            )

            val dataSources =
                editor.project?.let {
                    DataSourceManager.byDataSource(
                        it,
                        LocalDataSource::class.java,
                    )
                }?.dataSources ?: emptyList()

            ensureToolbarIsVisible(dataSources)
        }
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        Disposer.dispose(connection)
    }

    override fun <T : RawDataSource?> dataSourceAdded(
        manager: DataSourceManager<T>,
        dataSource: T & Any,
    ) {
        if (dataSource is LocalDataSource) {
            ensureToolbarIsVisible(manager?.dataSources as List<LocalDataSource> ?: emptyList())
        }
    }

    override fun <T : RawDataSource?> dataSourceRemoved(
        manager: DataSourceManager<T>,
        dataSource: T & Any,
    ) {
        if (dataSource is LocalDataSource) {
            ensureToolbarIsVisible(manager?.dataSources as List<LocalDataSource> ?: emptyList())
        }
    }

    override fun <T : RawDataSource?> dataSourceChanged(
        manager: DataSourceManager<T>?,
        dataSource: T?,
    ) {
        if (dataSource is LocalDataSource) {
            ensureToolbarIsVisible(manager?.dataSources as List<LocalDataSource> ?: emptyList())
        }
    }

    private fun ensureToolbarIsVisible(dataSources: List<LocalDataSource>) {
        toolbar.refreshDataSources(dataSources)

        val hasToolbar = editor.component.components.any { toolbar.isToolbar(it) }
        if (!hasToolbar) {
            val editorComponent = editor.component
            editorComponent.add(toolbar.panel, BorderLayout.NORTH)
        }
    }
}
