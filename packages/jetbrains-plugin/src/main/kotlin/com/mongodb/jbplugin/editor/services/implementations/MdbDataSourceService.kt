package com.mongodb.jbplugin.editor.services.implementations

import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.connection.ConnectionRequestor
import com.intellij.database.psi.DataSourceManager
import com.intellij.database.run.ConsoleRunConfiguration
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchChildBackground
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isMongoDbDataSource
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.editor.models.getToolbarModel
import com.mongodb.jbplugin.editor.services.DataSourceService
import com.mongodb.jbplugin.observability.useLogMessage
import io.ktor.util.collections.*
import kotlinx.coroutines.CoroutineScope

private val log = logger<MdbDataSourceService>()

/**
 * @param project
 * @param coroutineScope
 */
@Service(Service.Level.PROJECT)
class MdbDataSourceService(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) : DataSourceService {
    override fun listMongoDbDataSources(): List<LocalDataSource> =
        DataSourceManager.byDataSource(project, LocalDataSource::class.java)
            ?.dataSources?.filter { it.isMongoDbDataSource() }
            ?: emptyList()

    override fun listDatabasesForDataSource(dataSource: LocalDataSource) {
        val toolbarModel = project.getToolbarModel()
        coroutineScope.launchChildBackground {
            toolbarModel.databasesLoadingStarted(dataSource)
            try {
                val readModel = project.getService(DataGripBasedReadModelProvider::class.java)
                val databases = readModel.slice(dataSource, ListDatabases.Slice)
                toolbarModel.databasesLoadingSuccessful(
                    dataSource,
                    databases.databases.map { it.name }
                )
            } catch (exception: Exception) {
                log.error(
                    useLogMessage(
                        "Error while listing databases for DataSource(${dataSource.uniqueId})"
                    ).build(),
                    exception
                )
                toolbarModel.databasesLoadingFailed(dataSource, exception)
            }
        }
    }

    override fun connect(dataSource: LocalDataSource) {
        val toolbarModel = project.getToolbarModel()
        coroutineScope.launchChildBackground {
            toolbarModel.dataSourceConnectionStarted(dataSource)
            if (dataSource.isConnected()) {
                toolbarModel.dataSourceConnectionSuccessful(dataSource)
                return@launchChildBackground
            }

            try {
                val connectionManager = DatabaseConnectionManager.getInstance()
                val connectionHandler =
                    connectionManager
                        .build(project, dataSource)
                        .setRequestor(ConnectionRequestor.Anonymous())
                        .setAskPassword(true)
                        .setRunConfiguration(
                            ConsoleRunConfiguration.newConfiguration(project).apply {
                                setOptionsFromDataSource(dataSource)
                            },
                        )

                val connection = connectionHandler.create()?.get()
                if (connection == null || !dataSource.isConnected()) {
                    throw ConnectionNotConnectedException()
                }
                toolbarModel.dataSourceConnectionSuccessful(dataSource)
            } catch (exception: ConnectionNotConnectedException) {
                log.warn(
                    useLogMessage(
                        "Could not connect to DataSource(${dataSource.uniqueId})"
                    ).build(),
                    exception
                )
                toolbarModel.dataSourceConnectionUnsuccessful(dataSource)
            } catch (exception: Exception) {
                log.error(
                    useLogMessage(
                        "Error while connecting to DataSource(${dataSource.uniqueId})"
                    ).build(),
                    exception
                )
                toolbarModel.dataSourceConnectionFailed(dataSource, exception)
            }
        }
    }

    // Private only because it was not really needed outside the service class
    private class ConnectionNotConnectedException : Exception()
}

/**
 * Helper method to retrieve the MdbDataSourceService instance from Application
 *
 * @param project
 * @return
 */
fun Project.getDataSourceService(): DataSourceService = getService(
    MdbDataSourceService::class.java
)
