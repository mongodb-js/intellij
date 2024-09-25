/**
 * DatabaseModel: Contains an interface for interacting with the model and a sealed interface that represents the
 * different loading state for the DatabaseComboBox
 */

package com.mongodb.jbplugin.editor.models

import com.intellij.database.dataSource.LocalDataSource

/**
 * Represents different loading state for DatabaseComboBox
 */
sealed interface DatabasesComboBoxLoadingState {
    data object Started : DatabasesComboBoxLoadingState

    /**
     * @property databases
     * @property selectedDatabase
     */
    data class Finished(
        val databases: List<String>,
        val selectedDatabase: String?
    ) : DatabasesComboBoxLoadingState

    /**
     * @property exception
     */
    data class Errored(val exception: Exception) : DatabasesComboBoxLoadingState
}

/**
 * Interface for DatabaseComboBox to interact with DataSourceService and EditorService
 */
interface DatabaseModel {
    fun getStoredDatabase(): String?
    fun onDatabaseSelected(database: String)
    fun onDatabaseUnselected(database: String)

    // Not needed so far so commenting it out
    // fun listDatabases(onDatabasesLoadingStateChanged: (DatabasesLoadingState) -> Unit): List<String>
    fun loadComboBoxState(
        selectedDataSource: LocalDataSource,
        onComboBoxLoadingStateChanged: (DatabasesComboBoxLoadingState) -> Unit
    )
}
