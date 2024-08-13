package com.mongodb.jbplugin.editor.inputs

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.asSequence
import com.intellij.sql.indexOf
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.i18n.Icons.scaledToText
import com.mongodb.jbplugin.i18n.MdbToolbarMessages
import java.awt.event.ItemEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.SwingConstants

typealias DatabaseSelectedListener = (String) -> Unit;
typealias DatabaseUnselectedListener = () -> Unit;

class DatabaseComboBox(
    private val onDatabaseSelected: DatabaseSelectedListener,
    private val onDatabaseUnselected: DatabaseUnselectedListener,
) : ComboBox<String>() {

    var databases: List<String>
        set(value) {
            val selectedItem = this.selectedDatabase
            val model = model as DefaultComboBoxModel<String>
            model.removeAllElements()
            model.addElement(null)
            model.addAll(value)
            selectDatabaseByName(selectedItem)
        }
        get() =
            (model as DefaultComboBoxModel<String>).asSequence().toList().filterNotNull()

    var selectedDatabase: String?
        set(value) {
            selectedItem = value
        }
        get() = selectedItem as? String

    init {
        prototypeDisplayValue = "XXXXXXXXXXXXXX"

        addItemListener {
            if (it.stateChange == ItemEvent.DESELECTED) {
                onDatabaseUnselected()
            } else {
                onDatabaseSelected(it.item as String)
            }
        }
        putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
        setRenderer { _, value, index, _, _ ->
            if (value == null && index == -1) {
                JBLabel(
                    MdbToolbarMessages.message("attach.database.to.editor"),
                    Icons.databaseAutocompleteEntry.scaledToText(),
                    SwingConstants.LEFT,
                )
            } else {
                value?.let {
                    JBLabel(value, Icons.databaseAutocompleteEntry, SwingConstants.LEFT)
                } ?: JBLabel(
                    MdbToolbarMessages.message("detach.database.from.editor"),
                    Icons.remove.scaledToText(),
                    SwingConstants.LEFT,
                )
            }
        }
    }

    private fun selectDatabaseByName(name: String?) {
        name?.let {
            val databaseIndex =
                (model as DefaultComboBoxModel<String?>)
                    .asSequence()
                    .toList()
                    .indexOf { it == name }
            if (databaseIndex == -1) {
                selectedItem = null
            } else {
                selectedIndex = databaseIndex
            }
        } ?: run {
            selectedItem = null
        }
    }
}