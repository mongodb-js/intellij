package com.mongodb.jbplugin.editor.models.implementations

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.diagnostic.logger
import com.mongodb.jbplugin.editor.models.DatabaseModel
import com.mongodb.jbplugin.editor.models.DatabasesComboBoxLoadingState
import com.mongodb.jbplugin.editor.services.DataSourceService
import com.mongodb.jbplugin.editor.services.DatabasesLoadingState
import com.mongodb.jbplugin.editor.services.EditorService
import com.mongodb.jbplugin.editor.services.ToolbarSettings
import io.ktor.util.collections.*
import java.util.concurrent.atomic.AtomicBoolean

private val log = logger<ProjectDatabaseModel>()

/**
 * @param toolbarSettings
 * @param editorService
 * @param dataSourceService
 */
class ProjectDatabaseModel(
    private val toolbarSettings: ToolbarSettings,
    private val editorService: EditorService,
    private val dataSourceService: DataSourceService,
) : DatabaseModel {
    private val comboBoxLoadingStateListeners = ConcurrentMap<
        String,
        Set<
            (
                DatabasesComboBoxLoadingState
            ) -> Unit
            >
        >()
    private val isFetching = ConcurrentMap<String, AtomicBoolean>()

    override fun getStoredDatabase(): String? = toolbarSettings.database

    override fun onDatabaseSelected(database: String) {
        toolbarSettings.database = database
        editorService.attachDatabaseToSelectedEditor(database)
        editorService.reAnalyzeSelectedEditor(true)
    }

    override fun onDatabaseUnselected(database: String) {
        if (toolbarSettings.database == database) {
            toolbarSettings.database = null
        }
        editorService.detachDatabaseFromSelectedEditor(database)
        editorService.reAnalyzeSelectedEditor(true)
    }

    override fun loadComboBoxState(
        selectedDataSource: LocalDataSource,
        onComboBoxLoadingStateChanged: (DatabasesComboBoxLoadingState) -> Unit
    ) {
        enqueueComboBoxStateChangeListener(selectedDataSource, onComboBoxLoadingStateChanged)

        val fetchInProgress = isFetching.computeIfAbsent(selectedDataSource.uniqueId) {
            AtomicBoolean(false)
        }
        if (!fetchInProgress.getAndSet(true)) {
            fetchDatabasesForDataSource(selectedDataSource)
        }
    }

    private fun enqueueComboBoxStateChangeListener(
        dataSource: LocalDataSource,
        listener: (DatabasesComboBoxLoadingState) -> Unit
    ) {
        comboBoxLoadingStateListeners.merge(dataSource.uniqueId, setOf(listener)) {
                existingListeners,
                newListeners
            ->
            existingListeners + newListeners
        }
    }

    private fun fetchDatabasesForDataSource(selectedDataSource: LocalDataSource) {
        dataSourceService.listDatabasesForDataSource(selectedDataSource) { result ->
            val comboBoxLoadingState = mapToComboBoxLoadingState(result)
            notifyComboBoxStateListeners(selectedDataSource, comboBoxLoadingState)
            cleanUpListenersOnCompletion(selectedDataSource, comboBoxLoadingState)
        }
    }

    private fun mapToComboBoxLoadingState(
        databasesLoadingState: DatabasesLoadingState,
    ): DatabasesComboBoxLoadingState = when (databasesLoadingState) {
        is DatabasesLoadingState.Started -> DatabasesComboBoxLoadingState.Started
        is DatabasesLoadingState.Finished -> DatabasesComboBoxLoadingState.Finished(
            databases = databasesLoadingState.databases,
            selectedDatabase = if (toolbarSettings.database ==
                ToolbarSettings.UNINITIALIZED_DATABASE
            ) {
                editorService.inferredDatabase
            } else {
                toolbarSettings.database
            }
        )

        is DatabasesLoadingState.Errored -> DatabasesComboBoxLoadingState.Errored(
            databasesLoadingState.exception
        )
    }

    private fun notifyComboBoxStateListeners(
        selectedDataSource: LocalDataSource,
        comboBoxLoadingState: DatabasesComboBoxLoadingState
    ) {
        for (listener in comboBoxLoadingStateListeners[selectedDataSource.uniqueId] ?: setOf()) {
            try {
                listener(comboBoxLoadingState)
            } catch (exception: Exception) {
                log.warn(
                    "Failed to propagate DatabaseComboBoxLoadingStateListener for $selectedDataSource",
                    exception
                )
            }
        }
    }

    private fun cleanUpListenersOnCompletion(
        selectedDataSource: LocalDataSource,
        comboBoxLoadingState: DatabasesComboBoxLoadingState
    ) {
        // Remove listeners and isFetching state if the loading state has finished or errored
        if (comboBoxLoadingState is DatabasesComboBoxLoadingState.Finished ||
            comboBoxLoadingState is DatabasesComboBoxLoadingState.Errored
        ) {
            comboBoxLoadingStateListeners.remove(selectedDataSource.uniqueId)
            isFetching.remove(selectedDataSource.uniqueId)
        }
    }
}
