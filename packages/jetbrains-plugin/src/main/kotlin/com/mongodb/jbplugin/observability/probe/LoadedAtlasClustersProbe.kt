package com.mongodb.jbplugin.observability.probe

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.observability.LogMessage


private val logger: Logger = logger<LoadedAtlasClustersProbe>()

/**
 * This probe is emitted when the plugin is activated (started).
 *
 * @param project Project where the plugin is set up
 */
@Service(Service.Level.PROJECT)
class LoadedAtlasClustersProbe(private val project: Project) {
    fun atlasClustersLoaded(clusters: List<String>) {
        val logMessage = project.getService(LogMessage::class.java)

        logger.info(
            logMessage.message("Atlas Clusters loaded.")
                .put("clusters", clusters.joinToString(",") { it })
                .put("clusterCount", clusters.size)
                .build()
        )
    }
}