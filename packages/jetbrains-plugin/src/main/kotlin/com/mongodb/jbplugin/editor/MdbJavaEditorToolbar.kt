package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.asSequence
import com.intellij.sql.indexOf
import com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.i18n.Icons.scaledToText
import com.mongodb.jbplugin.i18n.MdbToolbarMessages
import java.awt.BorderLayout
import java.awt.event.ItemEvent.DESELECTED
import javax.swing.DefaultComboBoxModel
import javax.swing.SwingConstants

/**
 * Represents the toolbar that will be inserted into an active Java editor.
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
                JBLabel(MdbToolbarMessages.message("attach.datasource.to.editor"), Icons.Logo.scaledToText(), SwingConstants.LEFT)
            } else if (value == null) {
                JBLabel(MdbToolbarMessages.message("detach.datasource.from.editor"), Icons.Remove.scaledToText(), SwingConstants.LEFT)
            } else {
                val icon =
                    if (value.isConnected()) {
                        Icons.LogoConnected.scaledToText()
                    } else if (connecting) {
                        Icons.LoadingIcon.scaledToText()
                    } else if (failedConnection?.uniqueId == value.uniqueId) {
                        Icons.ConnectionFailed.scaledToText()
                    } else {
                        Icons.Logo.scaledToText()
                    }
                JBLabel(value.name, icon, SwingConstants.LEFT)
            }
        }
    }

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
        get() = (connectionComboBox.model as DefaultComboBoxModel<LocalDataSource>).asSequence().toList().filterNotNull()

    var selectedDataSource: LocalDataSource?
        set(value) {
            failedConnection = null
            connecting = false

            if (value == null) {
                connectionComboBox.selectedItem = null
            } else {
                connectionComboBox.selectedItem = value
            }
        }
        get() = connectionComboBox.selectedItem as? LocalDataSource

    private fun selectDataSourceWithId(id: String?) {
        if (id == null) {
            connectionComboBox.selectedItem = null
        } else {
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
        }
    }
}

typealias DataSourceSelectedListener = (LocalDataSource) -> Unit
typealias DataSourceUnselectedListener = () -> Unit
