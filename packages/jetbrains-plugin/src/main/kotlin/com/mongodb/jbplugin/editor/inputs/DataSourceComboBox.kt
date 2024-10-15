package com.mongodb.jbplugin.editor.inputs

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.asSequence
import com.intellij.sql.indexOf
import com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED
import com.intellij.ui.components.JBLabel
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.editor.models.getToolbarModel
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.i18n.Icons.scaledToText
import com.mongodb.jbplugin.i18n.MdbToolbarMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
class DataSourceComboBox(
    private val parent: JComponent,
    private val project: Project,
    coroutineScope: CoroutineScope,
) {
    private val comboBoxComponent = DataSourceComboBoxComponent()
    private val comboBoxModel
        get() = comboBoxComponent.model as DefaultComboBoxModel<LocalDataSource?>

    // UI related state
    private var selectedDataSourceConnecting = false
    private var selectedDataSourceFailedConnecting = false

    val dataSources
        get() = comboBoxModel.asSequence().toList().filterNotNull()

    val selectedDataSource
        get() = comboBoxComponent.selectedItem as LocalDataSource?

    private val selectionChangedListener: ItemListener = ItemListener { event ->
        selectedDataSourceConnecting = false
        selectedDataSourceFailedConnecting = false
        /**
         * No Selection -> Item selected -> onDataSourceSelected
         * Existing Selection -> Item deselected -> onDataSourceUnSelected
         * Existing Selection -> Item selection changed -> onDataSourceSelected
         *
         * When a selection is changed from one DataSource to another the selectedDataSource is
         * already set to the next DataSource and for that reason it is not really a deselection
         * which is why we ignore this event here and instead wait for the selection event that
         * follows right after this.
         */
        if (event.stateChange == ItemEvent.DESELECTED && selectedDataSource == null) {
            project.getToolbarModel().unselectDataSource(event.item as LocalDataSource)
        } else if (event.stateChange == ItemEvent.SELECTED) {
            project.getToolbarModel().selectDataSource(event.item as LocalDataSource)
        }
    }

    private val popupMenuListener = object : PopupMenuListener {
        override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) {}
        override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) {
            // We don't want to keep focus on the ComboBox because otherwise it hinders with the
            // selectionChanged event of FileEditorManager.Listener and the event never gets fired.
            // Doing this makes sure that the focus is not retained and transferred to the editor
            comboBoxComponent.transferFocus()
        }

        override fun popupMenuCanceled(event: PopupMenuEvent) {
            // Same stuff as above
            comboBoxComponent.transferFocus()
        }
    }

    init {
        val prototypeDataSource = object : LocalDataSource() {
            override fun getName(): String = "Attach MongoDB data source"
            override fun getUniqueId(): String = "attach_mongo_db_data_source"
        }
        comboBoxComponent.prototypeDisplayValue = prototypeDataSource
        comboBoxComponent.putClientProperty(ANIMATION_IN_RENDERER_ALLOWED, true)
        comboBoxComponent.addItemListener(selectionChangedListener)
        comboBoxComponent.addPopupMenuListener(popupMenuListener)
        comboBoxComponent.setRenderer { _, value, index, _, _ -> renderComboBoxItem(value, index) }

        var isFirstInit = true
        coroutineScope.launch {
            project.getToolbarModel().toolbarState.collect { state ->
                withoutSelectionChangedListener {
                    selectedDataSourceConnecting = state.selectedDataSourceConnecting
                    selectedDataSourceFailedConnecting = state.selectedDataSourceConnectionFailed
                    if (isFirstInit || state.dataSources != dataSources) {
                        isFirstInit = false
                        populateComboBoxWithDataSources(state.dataSources)
                    }
                    selectDataSourceByUniqueId(state.selectedDataSource?.uniqueId)
                }
            }
        }
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
        // When the selectedId and the provided id are the same then we simply ignore this call
        // because proceeding otherwise would lead to a deselection which we don't want
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

    private fun renderComboBoxItem(item: LocalDataSource?, index: Int): Component {
        println("index=$index, item=$item")
        return if (item == null && index == -1) {
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
                    } else if (selectedDataSourceConnecting) {
                        Icons.loading.scaledToText()
                    } else if (selectedDataSourceFailedConnecting) {
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
    }

    fun attachToParent() {
        if (!parent.components.contains(comboBoxComponent)) {
            parent.add(comboBoxComponent)
        }
    }

    // Subclassing ComboBox only because it makes creating test fixtures easier thanks to named
    // XPath queries
    private class DataSourceComboBoxComponent : ComboBox<LocalDataSource?>(DefaultComboBoxModel())
}
