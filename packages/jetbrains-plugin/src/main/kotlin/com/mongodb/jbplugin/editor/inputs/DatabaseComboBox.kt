package com.mongodb.jbplugin.editor.inputs

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.asSequence
import com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED
import com.intellij.ui.components.JBLabel
import com.mongodb.jbplugin.editor.models.DatabasesComboBoxLoadingState
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.i18n.Icons.scaledToText
import com.mongodb.jbplugin.i18n.MdbToolbarMessages
import java.awt.Component
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.SwingConstants

/**
 * @param parent
 * @param onDatabaseSelected
 * @param onDatabaseUnselected
 * @param initialDatabases
 * @param initialSelectedDatabase
 */
// Ktlint is reporting WRONG_WHITESPACE for line number 88 but everything seems alright there
@Suppress("WRONG_WHITESPACE")
class DatabaseComboBox(
    private val parent: JComponent,
    private val onDatabaseSelected: (String) -> Unit,
    private val onDatabaseUnselected: (String) -> Unit,
    initialDatabases: List<String> = emptyList(),
    initialSelectedDatabase: String? = null,
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
        loadingDatabases = false
        if (event.stateChange == ItemEvent.DESELECTED) {
            onDatabaseUnselected(event.item as String)
        } else if (event.stateChange == ItemEvent.SELECTED) {
            onDatabaseSelected(event.item as String)
        }
    }

    init {
        // Setting this ensures that our combobox renders with a width that would house the mentioned value at-least
        comboBoxComponent.prototypeDisplayValue = "XXXXXXXXXXXXXX"
        comboBoxComponent.putClientProperty(ANIMATION_IN_RENDERER_ALLOWED, true)
        comboBoxComponent.addItemListener(selectionChangedListener)
        comboBoxComponent.setRenderer { _, value, index, _, _ -> renderComboBoxItem(value, index) }

        populateComboBoxWithDatabases(initialDatabases)
        selectDatabaseAndNotify(initialSelectedDatabase)
    }

    private fun withoutSelectionChangedListener(block: () -> Unit) {
        comboBoxComponent.removeItemListener(selectionChangedListener)
        try {
            block()
        } finally {
            comboBoxComponent.addItemListener(selectionChangedListener)
        }
    }

    private fun populateComboBoxWithDatabases(databases: List<String>) {
        comboBoxModel.removeAllElements()
        // First item is purposely a null to render "Detach data source label"
        comboBoxModel.addElement(null)
        comboBoxModel.addAll(databases)
    }

    private fun selectDatabaseAndNotify(database: String?) {
        if (selectedDatabase == database) {
            return
        }
        comboBoxComponent.selectedItem = database
    }

    private fun renderComboBoxItem(item: String?, index: Int, ): Component = if (item == null && index == -1 &&
        loadingDatabases) {
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

    fun setComboBoxState(databases: List<String>, selectedDatabase: String?) = withoutSelectionChangedListener {
        populateComboBoxWithDatabases(databases)
        selectDatabaseAndNotify(selectedDatabase)
    }

    fun unselectDatabase(database: String) {
        if (selectedDatabase == database) {
            selectDatabaseAndNotify(null)
        }
    }

    fun onLoadingStateChanged(updatedLoadingState: DatabasesComboBoxLoadingState) {
        when (updatedLoadingState) {
            is DatabasesComboBoxLoadingState.Started -> loadingDatabases = true
            is DatabasesComboBoxLoadingState.Finished -> {
                loadingDatabases = false
                populateComboBoxWithDatabases(updatedLoadingState.databases)
                selectDatabaseAndNotify(updatedLoadingState.selectedDatabase)
            }

            is DatabasesComboBoxLoadingState.Errored -> loadingDatabases = false
            else -> {
                // ktlint thinks this is necessary despite having full coverage of sealed interface
            }
        }
    }

    // Subclassing ComboBox only because it makes creating test fixtures easier thanks to named XPath queries
    private class DatabaseComboBoxComponent : ComboBox<String?>(DefaultComboBoxModel(emptyArray()))
}
