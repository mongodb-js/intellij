package com.mongodb.jbplugin.i18n

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.NotRoamableUiSettings
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil
import java.awt.Component
import javax.swing.Icon
import javax.swing.SwingConstants

object Icons {
    private val greenCircle = IconLoader.getIcon("/icons/GreenCircle.svg", javaClass)
    val loading = AnimatedIcon.Default()
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
    private val collectionLight = IconLoader.getIcon("/icons/Collection.svg", javaClass)
    private val collectionDark = IconLoader.getIcon("/icons/CollectionDark.svg", javaClass)
    private val collection = if (JBColor.isBright()) collectionLight else collectionDark
    private val fieldLight = IconLoader.getIcon("/icons/Field.svg", javaClass)
    private val fieldDark = IconLoader.getIcon("/icons/FieldDark.svg", javaClass)
    private val field = if (JBColor.isBright()) fieldLight else fieldDark
    private val runQueryGutterLight = IconLoader.getIcon("/icons/ConsoleRun.svg", javaClass)
    private val runQueryGutterDark = IconLoader.getIcon("/icons/ConsoleRunDark.svg", javaClass)
    val runQueryGutter = if (JBColor.isBright()) runQueryGutterLight else runQueryGutterDark
    val databaseAutocompleteEntry = database
    val collectionAutocompleteEntry = collection
    val fieldAutocompleteEntry = field

    fun Icon.scaledToText(parentComponent: Component? = null): Icon {
        val settingsManager: NotRoamableUiSettings = NotRoamableUiSettings.getInstance()
        val settings = settingsManager.state
        return IconUtil.scaleByFont(this, parentComponent, settings.fontSize)
    }
}
