package com.mongodb.jbplugin.editor.models

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchChildBackground
import com.mongodb.jbplugin.editor.models.ToolbarEvent.DataSourceConnectionFailed
import com.mongodb.jbplugin.editor.models.ToolbarEvent.DataSourceConnectionStarted
import com.mongodb.jbplugin.editor.models.ToolbarEvent.DataSourceConnectionSuccessful
import com.mongodb.jbplugin.editor.models.ToolbarEvent.DataSourceConnectionUnsuccessful
import com.mongodb.jbplugin.editor.models.ToolbarEvent.DataSourceRemoved
import com.mongodb.jbplugin.editor.models.ToolbarEvent.DataSourceSelected
import com.mongodb.jbplugin.editor.models.ToolbarEvent.DataSourceTerminated
import com.mongodb.jbplugin.editor.models.ToolbarEvent.DataSourceUnselected
import com.mongodb.jbplugin.editor.models.ToolbarEvent.DataSourcesChanged
import com.mongodb.jbplugin.editor.models.ToolbarEvent.DatabaseSelected
import com.mongodb.jbplugin.editor.models.ToolbarEvent.DatabaseUnselected
import com.mongodb.jbplugin.editor.models.ToolbarEvent.DatabasesLoadingFailed
import com.mongodb.jbplugin.editor.models.ToolbarEvent.DatabasesLoadingStarted
import com.mongodb.jbplugin.editor.models.ToolbarEvent.DatabasesLoadingSuccessful
import com.mongodb.jbplugin.editor.services.ToolbarSettings.Companion.UNINITIALIZED_DATABASE
import com.mongodb.jbplugin.editor.services.implementations.getDataSourceService
import com.mongodb.jbplugin.editor.services.implementations.getEditorService
import com.mongodb.jbplugin.editor.services.implementations.getToolbarSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class ToolbarState(
    val selectedDataSource: LocalDataSource? = null,
    val selectedDataSourceConnecting: Boolean = false,
    val selectedDataSourceConnectionFailed: Boolean = false,
    val databasesLoadingForSelectedDataSource: Boolean = false,
    val databasesLoadingFailedForSelectedDataSource: Boolean = false,
    val databases: List<String> = emptyList(),
    val selectedDatabase: String? = null,
    private val project: Project,
    private val stateId: String = UUID.randomUUID().toString(),
) {
    val dataSources: List<LocalDataSource>
        get() {
            return project.getDataSourceService().listMongoDbDataSources()
        }
}

sealed interface ToolbarEvent {
    // From controller
    data object DataSourcesChanged : ToolbarEvent
    data class DataSourceRemoved(val dataSource: LocalDataSource) : ToolbarEvent
    data class DataSourceTerminated(val dataSource: LocalDataSource) : ToolbarEvent

    // From DataSourceComboBox
    data class DataSourceSelected(
        val dataSource: LocalDataSource,
        val isInitialSelection: Boolean = false,
    ) : ToolbarEvent
    data class DataSourceUnselected(val dataSource: LocalDataSource) : ToolbarEvent

    // From DataSourceService for DataSource
    data class DataSourceConnectionStarted(val dataSource: LocalDataSource) : ToolbarEvent
    data class DataSourceConnectionSuccessful(val dataSource: LocalDataSource) : ToolbarEvent
    data class DataSourceConnectionUnsuccessful(val dataSource: LocalDataSource) : ToolbarEvent
    data class DataSourceConnectionFailed(
        val dataSource: LocalDataSource,
        val exception: Exception,
    ) : ToolbarEvent

    // From DataSourceService for Database
    data class DatabasesLoadingStarted(val dataSource: LocalDataSource) : ToolbarEvent
    data class DatabasesLoadingSuccessful(
        val dataSource: LocalDataSource,
        val databases: List<String>
    ) : ToolbarEvent
    data class DatabasesLoadingFailed(
        val dataSource: LocalDataSource,
        val exception: Exception
    ) : ToolbarEvent

    // From DatabaseComboBox
    data class DatabaseSelected(val database: String) : ToolbarEvent
    data class DatabaseUnselected(val database: String) : ToolbarEvent
}

@Service(Service.Level.PROJECT)
class ToolbarModel(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    private val _toolbarState = MutableStateFlow(
        ToolbarState(project = project)
    )

    val toolbarState: StateFlow<ToolbarState> = _toolbarState

    private val toolbarEvents = MutableSharedFlow<ToolbarEvent>(
        replay = 1
    )

    private val isInitialised = AtomicBoolean(false)

    fun initialise() {
        if (isInitialised.compareAndSet(false, true)) {
            val toolbarSettings = project.getToolbarSettings()
            val dataSourceToBeSelected = _toolbarState.value.dataSources.find {
                it.uniqueId == toolbarSettings.dataSourceId
            }
            dataSourceToBeSelected?.let {
                coroutineScope.launch {
                    toolbarEvents.emit(DataSourceSelected(it, true))
                }
            }

            coroutineScope.launchChildBackground {
                toolbarEvents.collect { toolbarEvent ->
                    when (toolbarEvent) {
                        DataSourcesChanged -> handleDataSourcesChanged()
                        is DataSourceRemoved ->
                            handleDataSourceSelectionReverted(toolbarEvent.dataSource)
                        is DataSourceTerminated ->
                            handleDataSourceSelectionReverted(toolbarEvent.dataSource)
                        is DataSourceSelected -> handleDataSourceSelected(
                            toolbarEvent.dataSource,
                            toolbarEvent.isInitialSelection
                        )
                        is DataSourceUnselected ->
                            handleDataSourceSelectionReverted(toolbarEvent.dataSource)
                        is DataSourceConnectionStarted ->
                            handleDataSourceConnectionStarted(toolbarEvent.dataSource)
                        is DataSourceConnectionSuccessful ->
                            handleDataSourceConnectionSuccessful(toolbarEvent.dataSource)
                        is DataSourceConnectionUnsuccessful ->
                            handleDataSourceSelectionReverted(toolbarEvent.dataSource)
                        is DataSourceConnectionFailed ->
                            handleDataSourceConnectionFailed(toolbarEvent.dataSource)

                        is DatabasesLoadingStarted ->
                            handleDatabasesLoadingStarted(toolbarEvent.dataSource)
                        is DatabasesLoadingSuccessful -> handleDatabasesLoadingSuccessful(
                            toolbarEvent.dataSource,
                            toolbarEvent.databases
                        )
                        is DatabasesLoadingFailed ->
                            handleDatabasesLoadingFailed(toolbarEvent.dataSource)
                        is DatabaseSelected -> handleDatabaseSelected(toolbarEvent.database)
                        is DatabaseUnselected -> handleDatabaseUnSelected(toolbarEvent.database)
                    }
                }
            }
        }
    }

    fun selectDataSource(dataSource: LocalDataSource) {
        coroutineScope.launch { toolbarEvents.emit(DataSourceSelected(dataSource)) }
    }

    fun unselectDataSource(dataSource: LocalDataSource) {
        coroutineScope.launch { toolbarEvents.emit(DataSourceUnselected(dataSource)) }
    }

    fun selectDatabase(database: String) {
        coroutineScope.launch { toolbarEvents.emit(DatabaseSelected(database)) }
    }

    fun unselectDatabase(database: String) {
        coroutineScope.launch { toolbarEvents.emit(DatabaseUnselected(database)) }
    }

    fun dataSourcesChanged() {
        coroutineScope.launch { toolbarEvents.emit(DataSourcesChanged) }
    }

    fun dataSourceRemoved(dataSource: LocalDataSource) {
        coroutineScope.launch { toolbarEvents.emit(DataSourceRemoved(dataSource)) }
    }

    fun dataSourceTerminated(dataSource: LocalDataSource) {
        coroutineScope.launch { toolbarEvents.emit(DataSourceTerminated(dataSource)) }
    }

    suspend fun dataSourceConnectionStarted(dataSource: LocalDataSource) {
        toolbarEvents.emit(DataSourceConnectionStarted(dataSource))
    }

    suspend fun dataSourceConnectionSuccessful(dataSource: LocalDataSource) {
        toolbarEvents.emit(DataSourceConnectionSuccessful(dataSource))
    }

    suspend fun dataSourceConnectionUnsuccessful(dataSource: LocalDataSource) {
        toolbarEvents.emit(DataSourceConnectionUnsuccessful(dataSource))
    }

    suspend fun dataSourceConnectionFailed(dataSource: LocalDataSource, exception: Exception) {
        toolbarEvents.emit(DataSourceConnectionFailed(dataSource, exception))
    }

    suspend fun databasesLoadingStarted(dataSource: LocalDataSource) {
        toolbarEvents.emit(DatabasesLoadingStarted(dataSource))
    }

    suspend fun databasesLoadingSuccessful(dataSource: LocalDataSource, databases: List<String>) {
        toolbarEvents.emit(DatabasesLoadingSuccessful(dataSource, databases))
    }

    suspend fun databasesLoadingFailed(dataSource: LocalDataSource, exception: Exception) {
        toolbarEvents.emit(DatabasesLoadingFailed(dataSource, exception))
    }

    private fun handleDataSourcesChanged() {
        _toolbarState.update { it.copy(stateId = UUID.randomUUID().toString()) }
    }

    // Reverted in case of Termination, Removal, Un selection, Unsuccessful connection
    private fun handleDataSourceSelectionReverted(removedDataSource: LocalDataSource) {
        val oldState = _toolbarState.value
        val newState = _toolbarState.updateAndGet { previousState ->
            if (previousState.selectedDataSource?.uniqueId == removedDataSource.uniqueId) {
                previousState.copy(
                    selectedDataSource = null,
                    selectedDataSourceConnecting = false,
                    selectedDataSourceConnectionFailed = false,
                    databasesLoadingForSelectedDataSource = false,
                    databasesLoadingFailedForSelectedDataSource = false,
                    selectedDatabase = null,
                    databases = emptyList(),
                )
            } else {
                previousState
            }
        }

        if (newState.selectedDataSource == null) {
            val toolbarSettings = project.getToolbarSettings()
            toolbarSettings.dataSourceId = null
            toolbarSettings.database = null

            val editorService = project.getEditorService()
            editorService.detachDataSourceFromSelectedEditor(removedDataSource)
            oldState.selectedDatabase?.let { editorService.detachDatabaseFromSelectedEditor(it) }
            editorService.reAnalyzeSelectedEditor(applyReadAction = true)
        }
    }

    private fun handleDataSourceSelected(
        selectedDataSource: LocalDataSource,
        isInitialSelection: Boolean,
    ) {
        val oldState = _toolbarState.value
        val newState = _toolbarState.updateAndGet { previousState ->
            if (previousState.selectedDataSource?.uniqueId != selectedDataSource.uniqueId) {
                previousState.copy(
                    selectedDataSource = selectedDataSource,
                    selectedDataSourceConnecting = false,
                    selectedDataSourceConnectionFailed = false,
                    databasesLoadingForSelectedDataSource = false,
                    databasesLoadingFailedForSelectedDataSource = false,
                    databases = emptyList(),
                    selectedDatabase = null,
                )
            } else {
                previousState
            }
        }

        project.getDataSourceService().connect(selectedDataSource)

        if (
            newState.selectedDataSource?.uniqueId == selectedDataSource.uniqueId &&
            newState.selectedDatabase == null &&
            // We would like to reset the database to null only when it is not an initial selection
            !isInitialSelection
        ) {
            project.getToolbarSettings().database = null
            val editorService = project.getEditorService()
            oldState.selectedDatabase?.let { editorService.detachDatabaseFromSelectedEditor(it) }
            editorService.reAnalyzeSelectedEditor(applyReadAction = true)
        }
    }

    private fun handleDataSourceConnectionStarted(dataSource: LocalDataSource) {
        _toolbarState.update { previousState ->
            if (previousState.selectedDataSource?.uniqueId == dataSource.uniqueId) {
                previousState.copy(selectedDataSourceConnecting = true)
            } else {
                previousState
            }
        }
    }

    private fun handleDataSourceConnectionSuccessful(dataSource: LocalDataSource) {
        val newState = _toolbarState.updateAndGet { previousState ->
            if (previousState.selectedDataSource?.uniqueId == dataSource.uniqueId) {
                previousState.copy(selectedDataSourceConnecting = false)
            } else {
                previousState
            }
        }

        if (newState.selectedDataSource?.uniqueId == dataSource.uniqueId) {
            project.getToolbarSettings().dataSourceId = dataSource.uniqueId
            val editorService = project.getEditorService()
            editorService.attachDataSourceToSelectedEditor(dataSource)
            editorService.reAnalyzeSelectedEditor(applyReadAction = true)

            project.getDataSourceService().listDatabasesForDataSource(dataSource)
        }
    }

    private fun handleDataSourceConnectionFailed(dataSource: LocalDataSource) {
        _toolbarState.update { previousState ->
            if (previousState.selectedDataSource?.uniqueId == dataSource.uniqueId) {
                previousState.copy(
                    selectedDataSourceConnecting = false,
                    selectedDataSourceConnectionFailed = true,
                )
            } else {
                previousState
            }
        }
    }

    private fun handleDatabasesLoadingStarted(dataSource: LocalDataSource) {
        _toolbarState.update { previousState ->
            if (previousState.selectedDataSource?.uniqueId == dataSource.uniqueId) {
                previousState.copy(databasesLoadingForSelectedDataSource = true)
            } else {
                previousState
            }
        }
    }

    private fun handleDatabasesLoadingSuccessful(
        dataSource: LocalDataSource,
        databases: List<String>
    ) {
        val toolbarSettings = project.getToolbarSettings()
        val editorService = project.getEditorService()
        val newState = _toolbarState.updateAndGet { previousState ->
            if (previousState.selectedDataSource?.uniqueId == dataSource.uniqueId) {
                val databaseToBeSelected = if (toolbarSettings.database == UNINITIALIZED_DATABASE) {
                    editorService.inferredDatabase
                } else {
                    toolbarSettings.database
                }.takeIf { databases.contains(it) }

                previousState.copy(
                    databasesLoadingForSelectedDataSource = false,
                    selectedDatabase = databaseToBeSelected,
                    databases = databases
                )
            } else {
                previousState
            }
        }

        if (newState.selectedDatabase != null) {
            project.getToolbarSettings().database = newState.selectedDatabase
            editorService.attachDatabaseToSelectedEditor(newState.selectedDatabase)
            editorService.reAnalyzeSelectedEditor(applyReadAction = true)
        }
    }

    private fun handleDatabasesLoadingFailed(dataSource: LocalDataSource) {
        _toolbarState.update { previousState ->
            if (previousState.selectedDataSource?.uniqueId == dataSource.uniqueId) {
                previousState.copy(
                    databasesLoadingForSelectedDataSource = false,
                    databasesLoadingFailedForSelectedDataSource = true,
                )
            } else {
                previousState
            }
        }
    }

    private fun handleDatabaseSelected(database: String) {
        val newState = _toolbarState.updateAndGet { previousState ->
            if (
                previousState.selectedDatabase != database &&
                previousState.databases.contains(database)
            ) {
                previousState.copy(selectedDatabase = database)
            } else {
                previousState
            }
        }

        if (newState.selectedDatabase == database) {
            val editorService = project.getEditorService()
            project.getToolbarSettings().database = database
            editorService.attachDatabaseToSelectedEditor(database)
            editorService.reAnalyzeSelectedEditor(applyReadAction = true)
        }
    }

    private fun handleDatabaseUnSelected(database: String) {
        val newState = _toolbarState.updateAndGet { previousState ->
            if (
                previousState.selectedDatabase == database &&
                previousState.databases.contains(database)
            ) {
                previousState.copy(selectedDatabase = null)
            } else {
                previousState
            }
        }

        if (newState.selectedDatabase == null) {
            project.getToolbarSettings().database = null
            val editorService = project.getEditorService()
            editorService.attachDatabaseToSelectedEditor(database)
            editorService.reAnalyzeSelectedEditor(applyReadAction = true)
        }
    }
}

fun Project.getToolbarModel(): ToolbarModel = getService(ToolbarModel::class.java)
