/**
 * Action that loads all Atlas clusters for the current user.
 */

package com.mongodb.jbplugin.actions

import com.intellij.database.dataSource.DatabaseDriverManagerImpl
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.SchemaControl
import com.intellij.database.psi.DataSourceManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchChildOnUi
import com.mongodb.accessadapter.ClusterProvider
import com.mongodb.accessadapter.atlas.AtlasClusterProvider
import com.mongodb.jbplugin.observability.probe.LoadedAtlasClustersProbe
import com.mongodb.jbplugin.settings.MongoDbSettingsConfigurable
import com.mongodb.jbplugin.settings.useSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


/**
 * Service that implements the action.
 *
 * @param project
 * @param coroutineScope
 */
@Service(Service.Level.PROJECT)
internal class LoadAtlasClustersActionService(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) {
    fun loadClusters() = coroutineScope.launch {
        val settings = useSettings()
        if (settings.atlasApiKey.isBlank()) {
            launchChildOnUi {
                ShowSettingsUtil.getInstance().showSettingsDialog(
                    null,
                    MongoDbSettingsConfigurable::class.java
                )
            }
        }

        val (publicKey, privateKey) = settings.atlasApiKey.split(":")
        val clusterProvider: ClusterProvider = AtlasClusterProvider(publicKey, privateKey)

        val clusters = clusterProvider.getClusters()
        project.getService(LoadedAtlasClustersProbe::class.java).atlasClustersLoaded(clusters.map { it.clusterUrl })

        val dataSourceManager = DataSourceManager.byDataSource(project, LocalDataSource::class.java)
        val instance = DatabaseDriverManagerImpl.getInstance()
        val driver = instance.getDriver("mongo")

        dataSourceManager?.dataSources?.forEach { dataSource ->
            if (dataSource.groupName?.startsWith("Atlas") == true) {
                dataSourceManager.removeDataSource(dataSource)
            }
        }

        delay(500.milliseconds)
        val dataSources = clusters.map { cluster -> LocalDataSource().apply {
            name = cluster.clusterName
            setUrlSmart(cluster.clusterUrl)
            setSchemaControl(SchemaControl.AUTOMATIC)
            groupName = "Atlas / ${cluster.organizationName} / ${cluster.groupName}"
            databaseDriver = driver
            comment = "${cluster.groupId}"
        }}

        dataSources.forEach { dataSource ->
            dataSourceManager?.addDataSource(dataSource)
        }
    }
}

/**
 * Action that loads current account Atlas Clusters.
 */
class LoadAtlasClustersAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.project!!.getService(LoadAtlasClustersActionService::class.java).loadClusters()
    }
}