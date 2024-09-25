/**
 * DataSourceService: Contains an interface that outlines the helpers to interact with IntelliJ's DataSourceManager
 * as well as a few sealed interface to represent different loading states
 */

package com.mongodb.jbplugin.editor.services

import com.intellij.database.dataSource.LocalDataSource

/**
 * Represents different loading state for listing databases for DatabaseModel
 */
sealed interface DatabasesLoadingState {
    data object Started : DatabasesLoadingState

    /**
     * @property databases
     */
    data class Finished(val databases: List<String>) : DatabasesLoadingState

    /**
     * @property exception
     */
    data class Errored(val exception: Exception) : DatabasesLoadingState
}

/**
 * Represents different loading state of a Connection for DataSourceComboBox
 */
sealed interface ConnectionState {
    data object ConnectionStarted : ConnectionState
    data object ConnectionSuccess : ConnectionState
    data object ConnectionUnsuccessful : ConnectionState

    /**
     * @property failedDataSource
     */
    data class ConnectionFailed(val failedDataSource: LocalDataSource) : ConnectionState
}

/**
 * Interface that outlines helpers to interact with IntelliJ's DataSourceManager as well as our MongoDbDriver
 */
interface DataSourceService {
    fun listMongoDbDataSources(): List<LocalDataSource>
    fun listDatabasesForDataSource(
        dataSource: LocalDataSource,
        onLoadingStateChanged: (DatabasesLoadingState) -> Unit
    )
    fun connect(
        dataSource: LocalDataSource,
        onConnectionStateChanged: suspend (ConnectionState) -> Unit
    )
}
