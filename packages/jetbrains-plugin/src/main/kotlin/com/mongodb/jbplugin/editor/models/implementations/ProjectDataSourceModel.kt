package com.mongodb.jbplugin.editor.models.implementations

import com.intellij.database.dataSource.LocalDataSource
import com.mongodb.jbplugin.editor.models.DataSourceModel
import com.mongodb.jbplugin.editor.services.ConnectionState
import com.mongodb.jbplugin.editor.services.DataSourceService
import com.mongodb.jbplugin.editor.services.EditorService
import com.mongodb.jbplugin.editor.services.ToolbarSettings

/**
 * @param toolbarSettings
 * @param editorService
 * @param dataSourceService
 */
class ProjectDataSourceModel(
    private val toolbarSettings: ToolbarSettings,
    private val editorService: EditorService,
    private val dataSourceService: DataSourceService,
) : DataSourceModel {
    override fun getStoredDataSource(): LocalDataSource? = dataSourceService.listMongoDbDataSources().find {
        it.uniqueId == toolbarSettings.dataSourceId
    }

    override fun onDataSourceSelected(
        dataSource: LocalDataSource,
        onConnectionStateChanged: (ConnectionState) -> Unit
    ) {
        dataSourceService.connect(dataSource) {
            if (it is ConnectionState.ConnectionSuccess) {
                toolbarSettings.dataSourceId = dataSource.uniqueId
                editorService.attachDataSourceToSelectedEditor(dataSource)
                editorService.reAnalyzeSelectedEditor()
            }
            onConnectionStateChanged(it)
        }
    }

    override fun onDataSourceUnselected(dataSource: LocalDataSource) {
        if (dataSource.uniqueId == toolbarSettings.dataSourceId) {
            toolbarSettings.dataSourceId = null
        }
        editorService.detachDataSourceFromSelectedEditor(dataSource)
        editorService.reAnalyzeSelectedEditor()
    }

    override fun listDataSources() = dataSourceService.listMongoDbDataSources()

    override fun loadComboBoxState(): Pair<LocalDataSource?, List<LocalDataSource>> {
        val dataSources = dataSourceService.listMongoDbDataSources()
        val selectedDataSource = dataSources.find { it.uniqueId == toolbarSettings.dataSourceId }
        return selectedDataSource to dataSources
    }
}