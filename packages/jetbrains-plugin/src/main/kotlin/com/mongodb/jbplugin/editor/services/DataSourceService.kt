/**
 * DataSourceService: Contains an interface that outlines the helpers to interact with IntelliJ's DataSourceManager
 * as well as a few sealed interface to represent different loading states
 */

package com.mongodb.jbplugin.editor.services

import com.intellij.database.dataSource.LocalDataSource

/**
 * Interface that outlines helpers to interact with IntelliJ's DataSourceManager as well as our MongoDbDriver
 */
interface DataSourceService {
    fun listMongoDbDataSources(): List<LocalDataSource>
    fun listDatabasesForDataSource(dataSource: LocalDataSource)
    fun connect(dataSource: LocalDataSource)
}
