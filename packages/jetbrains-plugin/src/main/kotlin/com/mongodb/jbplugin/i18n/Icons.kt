package com.mongodb.jbplugin.i18n

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.NotRoamableUiSettings
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil
import java.awt.*
import javax.swing.Icon
import javax.swing.SwingConstants

object Icons {
    val loading = AnimatedIcon.Default()
    val greenCircle = IconLoader.getIcon("/icons/GreenCircle.svg", javaClass)
    val logo = AllIcons.Providers.MongoDB
    val logoConnected =
        LayeredIcon.layeredIcon(arrayOf(logo, greenCircle)).apply {
            val scaledGreenCircle = IconUtil.resizeSquared(greenCircle, 6)
            setIcon(scaledGreenCircle, 1, SwingConstants.SOUTH_EAST)
        }
    val connectionFailed = AllIcons.General.Error
    val remove = AllIcons.Diff.Remove
    private val databaseLight = IconLoader.getIcon("/icons/Database.svg", javaClass)
    private val databaseDark = IconLoader.getIcon("/icons/DatabaseDark.svg", javaClass)
    private val database = if (JBColor.isBright()) databaseLight else databaseDark
    private val fieldLight = IconLoader.getIcon("/icons/Field.svg", javaClass)
    private val fieldDark = IconLoader.getIcon("/icons/FieldDark.svg", javaClass)
    private val field = if (JBColor.isBright()) fieldLight else fieldDark
    val databaseAutocompleteEntry =
        LayeredIcon.layeredIcon(arrayOf(database, logo)).apply {
            val scaledGreenCircle = IconUtil.resizeSquared(logo, 8)
            setIcon(scaledGreenCircle, 1, SwingConstants.SOUTH_EAST)
        }
    val collectionAutocompleteEntry =
        LayeredIcon.layeredIcon(arrayOf(AllIcons.Nodes.DataTables, logo)).apply {
            val scaledGreenCircle = IconUtil.resizeSquared(logo, 8)
            setIcon(scaledGreenCircle, 1, SwingConstants.SOUTH_EAST)
        }
    val fieldAutocompleteEntry =
        LayeredIcon.layeredIcon(arrayOf(field, logo)).apply {
            val scaledGreenCircle = IconUtil.resizeSquared(logo, 8)
            setIcon(scaledGreenCircle, 1, SwingConstants.SOUTH_EAST)
        }

    fun Icon.scaledToText(parentComponent: Component? = null): Icon {
        val settingsManager: NotRoamableUiSettings = NotRoamableUiSettings.getInstance()
        val settings = settingsManager.state
        return IconUtil.scaleByFont(this, parentComponent, settings.fontSize)
    }
}
