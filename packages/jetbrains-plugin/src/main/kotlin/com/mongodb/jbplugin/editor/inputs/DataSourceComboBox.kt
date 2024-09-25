package com.mongodb.jbplugin.editor.inputs

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.asSequence
import com.intellij.sql.indexOf
import com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED
import com.intellij.ui.components.JBLabel
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.editor.services.ConnectionState
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.i18n.Icons.scaledToText
import com.mongodb.jbplugin.i18n.MdbToolbarMessages
import java.awt.Component
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.SwingConstants
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/**
 * @param parent
 * @param onDataSourceSelected
 * @param onDataSourceUnselected
 * @param initialDataSources
 * @param initialSelectedDataSource
 */
// Ktlint is reporting WRONG_WHITESPACE for line number 88 but everything seems alright there
@Suppress("WRONG_WHITESPACE")
class DataSourceComboBox(
    private val parent: JComponent,
    private val onDataSourceSelected: (source: LocalDataSource) -> Unit,
    private val onDataSourceUnselected: (source: LocalDataSource) -> Unit,
    initialDataSources: List<LocalDataSource>,
    initialSelectedDataSource: LocalDataSource?,
) {
    private val comboBoxComponent = DataSourceComboBoxComponent()
    private val comboBoxModel
        get() = comboBoxComponent.model as DefaultComboBoxModel<LocalDataSource?>

    // UI related state
    private var connecting = false
    private var failedConnection: LocalDataSource? = null

    val dataSources
        get() = comboBoxModel.asSequence().toList().filterNotNull()

    val selectedDataSource
        get() = comboBoxComponent.selectedItem as LocalDataSource?

    private val selectionChangedListener: ItemListener = ItemListener { event ->
        connecting = false
        failedConnection = null
        if (event.stateChange == ItemEvent.DESELECTED) {
            onDataSourceUnselected(event.item as LocalDataSource)
        } else {
            onDataSourceSelected(event.item as LocalDataSource)
        }
    }
    private val popupMenuListener = object : PopupMenuListener {
        override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) {}
        override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) {
            // We don't want to keep focus on the ComboBox because otherwise it hinders with the selectionChanged event
            // of FileEditorManager.Listener and the event never gets fired. Doing this makes sure that the focus is not
            // retained and transferred to the editor
            comboBoxComponent.transferFocus()
        }

        override fun popupMenuCanceled(event: PopupMenuEvent) {
            // Same stuff as above
            comboBoxComponent.transferFocus()
        }
    }

    init {
        comboBoxComponent.putClientProperty(ANIMATION_IN_RENDERER_ALLOWED, true)
        comboBoxComponent.addItemListener(selectionChangedListener)
        comboBoxComponent.addPopupMenuListener(popupMenuListener)
        comboBoxComponent.setRenderer { _, value, index, _, _ -> renderComboBoxItem(value, index) }

        populateComboBoxWithDataSources(initialDataSources)
        selectDataSourceByUniqueId(initialSelectedDataSource?.uniqueId)
    }

    private fun withoutSelectionChangedListener(block: () -> Unit) {
        comboBoxComponent.removeItemListener(selectionChangedListener)
        try {
            block()
        } finally {
            comboBoxComponent.addItemListener(selectionChangedListener)
        }
    }

    private fun populateComboBoxWithDataSources(dataSources: List<LocalDataSource>) {
        comboBoxModel.removeAllElements()
        // First item is purposely a null to render "Detach data source label"
        comboBoxModel.addElement(null)
        comboBoxModel.addAll(dataSources)
    }

    private fun selectDataSourceByUniqueId(uniqueId: String?) {
        // When the selectedId and the provided id are the same then we simply ignore this call because proceeding
        // otherwise would lead to a deselection which we don't want
        if (uniqueId == selectedDataSource?.uniqueId) {
            return
        }

        uniqueId?.let {
            val dataSourceIndex = comboBoxModel.asSequence().toList().indexOf {
                it?.uniqueId ==
                    uniqueId
            }
            if (dataSourceIndex >= 0) {
                comboBoxComponent.selectedIndex = dataSourceIndex
            } else {
                comboBoxComponent.selectedItem = null
            }
        } ?: run {
            comboBoxComponent.selectedItem = null
        }
    }

    private fun renderComboBoxItem(item: LocalDataSource?, index: Int): Component = if (item ==
        null &&
        index == -1
    ) {
        JBLabel(
            MdbToolbarMessages.message("attach.datasource.to.editor"),
            Icons.logo.scaledToText(),
            SwingConstants.LEFT,
        )
    } else {
        item?.let {
            val icon =
                if (item.isConnected()) {
                    Icons.logoConnected.scaledToText()
                } else if (connecting) {
                    Icons.loading.scaledToText()
                } else if (failedConnection?.uniqueId == item.uniqueId) {
                    Icons.connectionFailed.scaledToText()
                } else {
                    Icons.logo.scaledToText()
                }
            JBLabel(item.name, icon, SwingConstants.LEFT)
        } ?: JBLabel(
            MdbToolbarMessages.message("detach.datasource.from.editor"),
            Icons.remove.scaledToText(),
            SwingConstants.LEFT,
        )
    }

    fun attachToParent() {
        if (!parent.components.contains(comboBoxComponent)) {
            parent.add(comboBoxComponent)
        }
    }

    fun setComboBoxState(
        dataSources: List<LocalDataSource>,
        selectedDataSource: LocalDataSource?
    ) = withoutSelectionChangedListener {
        populateComboBoxWithDataSources(dataSources)
        selectDataSourceByUniqueId(selectedDataSource?.uniqueId)
    }

    fun unselectDataSource(dataSource: LocalDataSource) {
        if (selectedDataSource?.uniqueId == dataSource.uniqueId) {
            selectDataSourceByUniqueId(null)
        }
    }

    fun connectionStateChanged(connectionState: ConnectionState) {
        when (connectionState) {
            is ConnectionState.ConnectionStarted -> connecting = true
            is ConnectionState.ConnectionUnsuccessful -> {
                connecting = false
                selectDataSourceByUniqueId(null)
            }

            is ConnectionState.ConnectionSuccess -> connecting = false
            is ConnectionState.ConnectionFailed -> {
                connecting = false
                failedConnection = connectionState.failedDataSource
            }
        }
    }

    // Subclassing ComboBox only because it makes creating test fixtures easier thanks to named XPath queries
    private class DataSourceComboBoxComponent : ComboBox<LocalDataSource?>(DefaultComboBoxModel())
}
