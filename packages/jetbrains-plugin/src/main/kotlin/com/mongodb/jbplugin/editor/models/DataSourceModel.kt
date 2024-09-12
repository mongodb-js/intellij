package com.mongodb.jbplugin.editor.models

import com.intellij.database.dataSource.LocalDataSource
import com.mongodb.jbplugin.editor.services.*

/**
 * Interface for DataSourceComboBox to interact with DataSourceService and EditorService
 */
interface DataSourceModel {
    fun getStoredDataSource(): LocalDataSource?
    fun onDataSourceSelected(dataSource: LocalDataSource, onConnectionStateChanged: (ConnectionState) -> Unit)
    fun onDataSourceUnselected(dataSource: LocalDataSource)
    fun listDataSources(): List<LocalDataSource>
    fun loadComboBoxState(): Pair<LocalDataSource?, List<LocalDataSource>>
}
