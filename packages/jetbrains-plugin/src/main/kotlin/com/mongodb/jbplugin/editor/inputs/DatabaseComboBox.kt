package com.mongodb.jbplugin.editor.inputs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.asSequence
import com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED
import com.intellij.ui.components.JBLabel
import com.mongodb.jbplugin.editor.models.ToolbarState
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
import javax.swing.SwingUtilities
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/**
 * @param parent
 * @param onDatabaseSelected
 * @param onDatabaseUnselected
 * @param initialDatabases
 * @param initialSelectedDatabase
 */
class DatabaseComboBox(
    private val parent: JComponent,
    private val project: Project,
    coroutineScope: CoroutineScope,
) {
    private val comboBoxComponent = DatabaseComboBoxComponent()
    private val comboBoxModel
        get() = comboBoxComponent.model as DefaultComboBoxModel<String?>

    // UI related state
    private var loadingDatabases = false

    val databases
        get() = comboBoxModel.asSequence().toList().filterNotNull()

    val selectedDatabase
        get() = comboBoxModel.selectedItem as String?

    private val selectionChangedListener: ItemListener = ItemListener { event ->
        if (event.stateChange == ItemEvent.DESELECTED && selectedDatabase == null) {
            project.getToolbarModel().unselectDatabase(event.item as String)
        } else if (event.stateChange == ItemEvent.SELECTED) {
            project.getToolbarModel().selectDatabase(event.item as String)
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
        // Setting this ensures that our combobox renders with a width that would house the mentioned value at-least
        comboBoxComponent.prototypeDisplayValue = "XXXXXXXXXXXXXX"
        comboBoxComponent.putClientProperty(ANIMATION_IN_RENDERER_ALLOWED, true)
        comboBoxComponent.addItemListener(selectionChangedListener)
        comboBoxComponent.addPopupMenuListener(popupMenuListener)
        comboBoxComponent.setRenderer { _, value, index, _, _ -> renderComboBoxItem(value, index) }

        var isFirstInit = true
        coroutineScope.launch {
            project.getToolbarModel().toolbarState.collect { state ->
                SwingUtilities.invokeLater {
                    updateComboBoxState(state, isFirstInit)
                    isFirstInit = false
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

    private fun updateComboBoxState(state: ToolbarState, isFirstInit: Boolean) = withoutSelectionChangedListener {
        loadingDatabases = state.databasesLoadingForSelectedDataSource
        if (isFirstInit || state.databases != databases) {
            populateComboBoxWithDatabases(state.databases.toSet())
        }
        selectDatabaseAndNotify(state.selectedDatabase)
    }

    private fun populateComboBoxWithDatabases(newDatabases: Set<String>) {
        val oldDatabases = databases.toSet()

        val databasesToAdd = newDatabases - oldDatabases
        val databasesToRemove = oldDatabases - newDatabases

        databasesToAdd.forEach { comboBoxModel.addElement(it) }
        databasesToRemove.forEach { comboBoxModel.removeElement(it) }

        if (comboBoxModel.getIndexOf(null) != 0) {
            comboBoxModel.removeElement(null)
            comboBoxModel.insertElementAt(null, 0)
        }
    }

    private fun selectDatabaseAndNotify(database: String?) {
        if (selectedDatabase == database) {
            return
        }
        comboBoxComponent.selectedItem = database
    }

    private fun renderComboBoxItem(item: String?, index: Int): Component = if (item == null &&
        index == -1 &&
        loadingDatabases
    ) {
        JBLabel("Loading databases...", Icons.loading.scaledToText(), SwingConstants.LEFT)
    } else if (item == null && index == -1) {
        JBLabel(
            MdbToolbarMessages.message("attach.database.to.editor"),
            Icons.databaseAutocompleteEntry.scaledToText(),
            SwingConstants.LEFT,
        )
    } else {
        item?.let {
            JBLabel(item, Icons.databaseAutocompleteEntry, SwingConstants.LEFT)
        } ?: JBLabel(
            MdbToolbarMessages.message("detach.database.from.editor"),
            Icons.remove.scaledToText(),
            SwingConstants.LEFT,
        )
    }

    fun attachToParent() {
        if (!parent.components.contains(comboBoxComponent)) {
            parent.add(comboBoxComponent)
        }
    }

    fun removeFromParent() {
        if (parent.components.contains(comboBoxComponent)) {
            parent.remove(comboBoxComponent)
        }
    }

    // Subclassing ComboBox only because it makes creating test fixtures easier thanks to named XPath queries
    private class DatabaseComboBoxComponent : ComboBox<String?>(DefaultComboBoxModel(emptyArray()))
}
