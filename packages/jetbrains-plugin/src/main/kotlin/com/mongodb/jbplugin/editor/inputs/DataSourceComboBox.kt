package com.mongodb.jbplugin.editor.inputs

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.asSequence
import com.intellij.sql.indexOf
import com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED
import com.intellij.ui.components.JBLabel
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.i18n.Icons.scaledToText
import com.mongodb.jbplugin.i18n.MdbToolbarMessages
import java.awt.event.ItemEvent.DESELECTED
import javax.swing.DefaultComboBoxModel
import javax.swing.SwingConstants

typealias DataSourceSelectedListener = (LocalDataSource) -> Unit
typealias DataSourceUnselectedListener = () -> Unit

class DataSourceComboBox(
    private val onDataSourceSelected: DataSourceSelectedListener,
    private val onDataSourceUnselected: DataSourceUnselectedListener,
) : ComboBox<LocalDataSource>(
    DefaultComboBoxModel(emptyArray())
) {
    var connecting: Boolean = false
    var failedConnection: LocalDataSource? = null

    var dataSources: List<LocalDataSource>
        set(value) {
            failedConnection = null
            connecting = false

            val selectedItem = this.selectedDataSource?.uniqueId
            val model = model as DefaultComboBoxModel<LocalDataSource>
            model.removeAllElements()
            model.addElement(null)
            model.addAll(value)
            selectDataSourceWithId(selectedItem)
        }
        get() =
            (model as DefaultComboBoxModel<LocalDataSource>).asSequence().toList().filterNotNull()

    var selectedDataSource: LocalDataSource?
        set(value) {
            failedConnection = null
            connecting = false

            value?.let {
                selectedItem = value
            } ?: run {
                selectedItem = null
            }
        }
        get() = selectedItem as? LocalDataSource

    init {
        addItemListener {
            failedConnection = null
            connecting = false

            if (it.stateChange == DESELECTED) {
                onDataSourceUnselected()
            } else {
                onDataSourceSelected(it.item as LocalDataSource)
            }
        }
        putClientProperty(ANIMATION_IN_RENDERER_ALLOWED, true)
        setRenderer { _, value, index, _, _ ->
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
                (model as DefaultComboBoxModel<LocalDataSource?>)
                    .asSequence()
                    .toList()
                    .indexOf { it?.uniqueId == id }
            if (dataSourceIndex == -1) {
                selectedItem = null
            } else {
                selectedIndex = dataSourceIndex
            }
        } ?: run {
            selectedItem = null
        }
    }
}