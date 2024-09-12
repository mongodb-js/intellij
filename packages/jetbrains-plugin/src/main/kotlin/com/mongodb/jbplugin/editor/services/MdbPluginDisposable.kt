package com.mongodb.jbplugin.editor.services

import com.intellij.configurationStore.StoreUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.ktor.util.collections.*

private val log = logger<MdbPluginDisposable>()

/**
 * Since our Plugin acts mostly on Project level and initialises different resources / sets up subscriptions, some of
 * them might require clean up. Services initialised by IntelliJ are automatically cleaned up but manually set up
 * subscriptions and acquired resources needs a parent disposable to tie their lifecycle to. This class acts our
 * disposable parent for such subscriptions and resources which we may want to clean up when a project is closed or the
 * plugin is unloaded
 */
@Service(Service.Level.PROJECT)
class MdbPluginDisposable : Disposable {
    private val onDisposeListeners = ConcurrentSet<() -> Unit>()

    /**
     * Registers a listener for when the service is disposed which happens either when the project is closed
     * or when the plugin is unloaded
     *
     * @param block - Listener that gets called with the service is being disposed
     */
    fun onDispose(block: () -> Unit) {
        onDisposeListeners.add(block)
    }

    /**
     * Disposable interface that gets called when this service is being disposed
     */
    override fun dispose() {
        // At the very least we would always want to persist our modified settings (ToolbarSettings, PluginSettings)
        // when closing the project which is why we explicitly invoke StoreUtil.saveSettings to persist them to disk
        ApplicationManager.getApplication().invokeLater {
            StoreUtil.saveSettings(ApplicationManager.getApplication())
        }

        val listeners = onDisposeListeners.toList()
        onDisposeListeners.clear()
        for (listener in listeners) {
            try {
                listener()
            } catch (exception: Exception) {
                log.warn("Exception while running an onDispose listener", exception)
            }
        }
    }

    companion object {
        /**
         * Retrieves an instance of the MdbPluginDisposable from the project
         *
         * @param project
         * @return
         */
        fun getInstance(project: Project): MdbPluginDisposable = project.getService(MdbPluginDisposable::class.java)
    }
}