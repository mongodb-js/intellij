package com.mongodb.jbplugin.i18n

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.NotRoamableUiSettings
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil
import java.awt.*
import javax.swing.Icon
import javax.swing.SwingConstants

object Icons {
    val LoadingIcon = AnimatedIcon.Default()
    val GreenCircle = IconLoader.getIcon("/icons/GreenCircle.svg", javaClass)

    val Logo = AllIcons.Providers.MongoDB
    val LogoConnected =
        LayeredIcon.layeredIcon(arrayOf(Logo, GreenCircle)).apply {
            val scaledGreenCircle = IconUtil.resizeSquared(GreenCircle, 6)
            setIcon(scaledGreenCircle, 1, SwingConstants.SOUTH_EAST)
        }

    val ConnectionFailed = AllIcons.General.Error
    val Remove = AllIcons.Diff.Remove

    fun Icon.scaledToText(parentComponent: Component? = null): Icon {
        val settingsManager: NotRoamableUiSettings = NotRoamableUiSettings.getInstance()
        val settings = settingsManager.state
        return IconUtil.scaleByFont(this, parentComponent, settings.fontSize)
    }
}
