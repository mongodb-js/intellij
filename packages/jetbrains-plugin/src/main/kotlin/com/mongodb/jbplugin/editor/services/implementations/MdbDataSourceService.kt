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
import com.mongodb.jbplugin.editor.services.ConnectionState
import com.mongodb.jbplugin.editor.services.DataSourceService
import com.mongodb.jbplugin.editor.services.DatabasesLoadingState
import kotlinx.coroutines.CoroutineScope

private val log = logger<MdbDataSourceService>()

/**
 * @param project
 * @param coroutineScope
 */
// Ktlint complains about line 33 being too long but there is nothing there
@Suppress("LONG_LINE")
@Service(Service.Level.PROJECT)
class MdbDataSourceService(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) : DataSourceService {
    override fun listMongoDbDataSources(): List<LocalDataSource> =
        DataSourceManager.byDataSource(project, LocalDataSource::class.java)
            ?.dataSources?.filter { it.isMongoDbDataSource() }
            ?: emptyList()

    override fun listDatabasesForDataSource(
        dataSource: LocalDataSource,
        onLoadingStateChanged: (DatabasesLoadingState) -> Unit
    ) {
        coroutineScope.launchChildBackground {
            onLoadingStateChanged(DatabasesLoadingState.Started)
            try {
                val readModel = project.getService(DataGripBasedReadModelProvider::class.java)
                val databases = readModel.slice(dataSource, ListDatabases.Slice)
                onLoadingStateChanged(
                    DatabasesLoadingState.Finished(databases.databases.map { it.name })
                )
            } catch (exception: Exception) {
                log.error(
                    "Error while listing databases for DataSource(${dataSource.uniqueId})",
                    exception
                )
                onLoadingStateChanged(DatabasesLoadingState.Errored(exception))
            }
        }
    }

    override fun connect(
        dataSource: LocalDataSource,
        onConnectionStateChanged: suspend (ConnectionState) -> Unit
    ) {
        coroutineScope.launchChildBackground {
            onConnectionStateChanged(ConnectionState.ConnectionStarted)
            if (dataSource.isConnected()) {
                onConnectionStateChanged(ConnectionState.ConnectionSuccess)
                return@launchChildBackground
            }

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

            try {
                val connection = connectionHandler.create()?.get()
                if (connection == null || !dataSource.isConnected()) {
                    throw ConnectionNotConnectedException()
                }
                onConnectionStateChanged(ConnectionState.ConnectionSuccess)
            } catch (exception: ConnectionNotConnectedException) {
                log.warn(
                    "Could not connect to DataSource(${dataSource.uniqueId})",
                    exception
                )
                onConnectionStateChanged(ConnectionState.ConnectionUnsuccessful)
            } catch (exception: Exception) {
                log.error(
                    "Error while connecting to DataSource(${dataSource.uniqueId})",
                    exception
                )
                onConnectionStateChanged(ConnectionState.ConnectionFailed(dataSource))
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
fun getDataSourceService(project: Project): DataSourceService = project.getService(
    MdbDataSourceService::class.java
)
