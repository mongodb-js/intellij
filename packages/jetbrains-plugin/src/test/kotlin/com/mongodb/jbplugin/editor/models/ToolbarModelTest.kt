package com.mongodb.jbplugin.editor.models

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.editor.services.ToolbarSettings.Companion.UNINITIALIZED_DATABASE
import com.mongodb.jbplugin.editor.services.implementations.MdbDataSourceService
import com.mongodb.jbplugin.editor.services.implementations.MdbEditorService
import com.mongodb.jbplugin.editor.services.implementations.PersistentToolbarSettings
import com.mongodb.jbplugin.editor.services.implementations.ToolbarSettingsStateComponent
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.mockDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class ToolbarModelTest {

    private lateinit var project: Project
    private lateinit var coroutineScope: CoroutineScope
    private lateinit var toolbarSettings: PersistentToolbarSettings
    private lateinit var dataSourceService: MdbDataSourceService
    private lateinit var editorService: MdbEditorService
    private lateinit var toolbarModel: ToolbarModel
    private lateinit var dataSource: LocalDataSource

    @BeforeEach
    fun beforeEach() {
        project = mock<Project>()
        coroutineScope = TestScope()

        toolbarSettings = mock<PersistentToolbarSettings>()
        val toolbarSettingsStateComponent = mock<ToolbarSettingsStateComponent>()
        `when`(project.getService(ToolbarSettingsStateComponent::class.java)).thenReturn(
            toolbarSettingsStateComponent
        )
        `when`(toolbarSettingsStateComponent.state).thenReturn(toolbarSettings)

        dataSourceService = mock<MdbDataSourceService>()
        `when`(project.getService(MdbDataSourceService::class.java)).thenReturn(dataSourceService)

        editorService = mock<MdbEditorService>()
        `when`(project.getService(MdbEditorService::class.java)).thenReturn(editorService)

        toolbarModel = ToolbarModel(project, coroutineScope)
        `when`(project.getService(ToolbarModel::class.java)).thenReturn(toolbarModel)

        dataSource = mockDataSource()
        `when`(dataSource.uniqueId).thenReturn("mocked-data-source-unique-id")
        `when`(dataSource.name).thenReturn("mocked-data-source")
    }

    @Nested
    @DisplayName("when initialised")
    inner class ToolbarModelInitialised {
        @Test
        fun `restores last selected dataSource and initiate a connection attempt`() {
            `when`(toolbarSettings.dataSourceId).thenReturn("mocked-data-source-unique-id")
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))

            toolbarModel.initialise()
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(
                    toolbarState.selectedDataSource?.uniqueId,
                    dataSource.uniqueId
                )
            }
            verify(dataSourceService, times(1)).connect(dataSource)
        }

        @Test
        fun `will not attempt to detach the datasource and database from Editor for restored selection`() {
            `when`(toolbarSettings.dataSourceId).thenReturn("mocked-data-source-unique-id")
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()

            // Wait for state update
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(
                    toolbarState.selectedDataSource?.uniqueId,
                    dataSource.uniqueId
                )
            }
            verify(editorService, times(0)).detachDataSourceFromSelectedEditor(any())
            verify(editorService, times(0)).detachDatabaseFromSelectedEditor(any())
        }

        @Test
        fun `will not restore the last selection if the selected data source is not present`() {
            `when`(toolbarSettings.dataSourceId).thenReturn("mocked-data-source-unique-id")
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(emptyList())

            toolbarModel.initialise()
            val newToolbarState = toolbarModel.toolbarState.value
            assertNull(newToolbarState.selectedDataSource)
        }
    }

    @Nested
    @DisplayName("when a DataSource is selected")
    inner class DataSourceSelected {
        @Test
        fun `should update the state with the selected data source and start connecting`() {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                // Should also reset the rest of the state dependent on the DataSource selection
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
                assertEquals(toolbarState.selectedDataSourceConnectionFailed, false)
                assertEquals(toolbarState.selectedDatabase, null)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databasesLoadingFailedForSelectedDataSource, false)
                assertEquals(toolbarState.databases, emptyList<String>())
            }
            verify(dataSourceService, times(1)).connect(dataSource)
        }

        @Test
        fun `should attempt to detach the previously selected datasource and database from Editor`() {
            val previousDataSource = mockDataSource()
            `when`(previousDataSource.uniqueId).thenReturn("previous-data-source-unique-id")
            `when`(toolbarSettings.dataSourceId).thenReturn("previous-data-source-unique-id")
            `when`(
                dataSourceService.listMongoDbDataSources()
            ).thenReturn(listOf(dataSource, previousDataSource))
            toolbarModel.initialise()
            // restore to take effect
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, previousDataSource.uniqueId)
            }

            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
            }
            verify(dataSourceService, times(1)).connect(dataSource)
        }
    }

    @Nested
    @DisplayName("when connection state changes")
    inner class ConnectionStateChanged {
        @Test
        fun `when connection starts, it should update the state`() = runBlocking {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
            }

            toolbarModel.dataSourceConnectionStarted(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, true)
            }
        }

        @Test
        fun `when connection is successful, it should update the state`() = runBlocking {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
            }

            toolbarModel.dataSourceConnectionStarted(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, true)
            }

            toolbarModel.dataSourceConnectionSuccessful(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
            }
        }

        @Test
        fun `when connection is successful, it should update ToolbarSettings, EditorService and also start loading databases`() = runBlocking {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
            }

            toolbarModel.dataSourceConnectionStarted(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, true)
            }

            toolbarModel.dataSourceConnectionSuccessful(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
            }

            verify(toolbarSettings).dataSourceId = dataSource.uniqueId
            verify(editorService).attachDataSourceToSelectedEditor(dataSource)
            verify(editorService, times(2)).reAnalyzeSelectedEditor(applyReadAction = true)
            verify(dataSourceService).listDatabasesForDataSource(dataSource)
        }

        @Test
        fun `when connection is unsuccessful, it should revert the selection, update the state, ToolbarSettings and EditorService`() = runBlocking {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
            }

            toolbarModel.dataSourceConnectionUnsuccessful(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource, null)
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
                assertEquals(toolbarState.selectedDataSourceConnectionFailed, false)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databasesLoadingFailedForSelectedDataSource, false)
                assertEquals(toolbarState.selectedDatabase, null)
                assertEquals(toolbarState.databases, emptyList<String>())
            }

            verify(toolbarSettings).dataSourceId = null
            verify(toolbarSettings, times(2)).database = null
            verify(editorService).detachDataSourceFromSelectedEditor(dataSource)
            verify(editorService, times(2)).reAnalyzeSelectedEditor(applyReadAction = true)
        }

        @Test
        fun `when connection is failed, it should update the state`() = runBlocking {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
            }

            toolbarModel.dataSourceConnectionFailed(dataSource, mock<Exception>())
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSourceConnectionFailed, true)
            }
        }
    }

    @Nested
    @DisplayName("when databases loading state changes")
    inner class DatabasesLoadingStateChanged {
        @Test
        fun `when databases loading starts, it updates the state`() = runBlocking {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
            }

            toolbarModel.dataSourceConnectionStarted(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, true)
            }

            toolbarModel.dataSourceConnectionSuccessful(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
            }
            verify(dataSourceService).listDatabasesForDataSource(dataSource)

            toolbarModel.databasesLoadingStarted(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, true)
            }
        }

        @Test
        fun `when databases loading failed, it updates the state`() = runBlocking {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
            }

            toolbarModel.dataSourceConnectionStarted(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, true)
            }

            toolbarModel.dataSourceConnectionSuccessful(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
            }
            verify(dataSourceService).listDatabasesForDataSource(dataSource)

            toolbarModel.databasesLoadingStarted(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, true)
            }

            toolbarModel.databasesLoadingFailed(dataSource, mock<Exception>())
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databasesLoadingFailedForSelectedDataSource, true)
            }
        }

        @Test
        fun `when databases loading is successful, it updates the state`() = runBlocking {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
            }

            toolbarModel.dataSourceConnectionStarted(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, true)
            }

            toolbarModel.dataSourceConnectionSuccessful(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
            }
            verify(dataSourceService).listDatabasesForDataSource(dataSource)

            toolbarModel.databasesLoadingStarted(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, true)
            }

            toolbarModel.databasesLoadingSuccessful(dataSource, listOf("mongodb", "mangodb"))
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databases, listOf("mongodb", "mangodb"))
                assertEquals(toolbarState.selectedDatabase, null)
            }
        }

        @Test
        fun `when databases loading is successful, it restores the previous selection if it is present in databases list`() = runBlocking {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
            }

            toolbarModel.dataSourceConnectionStarted(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, true)
            }

            toolbarModel.dataSourceConnectionSuccessful(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
            }
            verify(dataSourceService).listDatabasesForDataSource(dataSource)

            toolbarModel.databasesLoadingStarted(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, true)
            }

            `when`(toolbarSettings.database).thenReturn("mongodb")
            toolbarModel.databasesLoadingSuccessful(dataSource, listOf("mongodb", "mangodb"))
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databases, listOf("mongodb", "mangodb"))
                assertEquals(toolbarState.selectedDatabase, "mongodb")
            }

            verify(toolbarSettings).database = "mongodb"
            verify(editorService).attachDatabaseToSelectedEditor("mongodb")
            verify(editorService, times(3)).reAnalyzeSelectedEditor(applyReadAction = true)
        }

        @Test
        fun `when databases loading is successful, it will not restore the previous selection if it is not present in databases list`() = runBlocking {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
            }

            toolbarModel.dataSourceConnectionStarted(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, true)
            }

            toolbarModel.dataSourceConnectionSuccessful(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
            }
            verify(dataSourceService).listDatabasesForDataSource(dataSource)

            toolbarModel.databasesLoadingStarted(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, true)
            }

            `when`(toolbarSettings.database).thenReturn("bongodb")
            toolbarModel.databasesLoadingSuccessful(dataSource, listOf("mongodb", "mangodb"))
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databases, listOf("mongodb", "mangodb"))
                assertEquals(toolbarState.selectedDatabase, null)
            }
        }

        @Test
        fun `when databases loading is successful and the database was never selected, it will select the inferred database from editor service`() = runBlocking {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
            }

            toolbarModel.dataSourceConnectionStarted(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, true)
            }

            toolbarModel.dataSourceConnectionSuccessful(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
            }
            verify(dataSourceService).listDatabasesForDataSource(dataSource)

            toolbarModel.databasesLoadingStarted(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, true)
            }

            `when`(toolbarSettings.database).thenReturn(UNINITIALIZED_DATABASE)
            `when`(editorService.inferredDatabase).thenReturn("mangodb")
            toolbarModel.databasesLoadingSuccessful(dataSource, listOf("mongodb", "mangodb"))
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databases, listOf("mongodb", "mangodb"))
                assertEquals(toolbarState.selectedDatabase, "mangodb")
            }

            verify(toolbarSettings).database = "mangodb"
            verify(editorService).attachDatabaseToSelectedEditor("mangodb")
            verify(editorService, times(3)).reAnalyzeSelectedEditor(applyReadAction = true)
        }
    }

    @Nested
    @DisplayName("when a DataSource is unselected")
    inner class DataSourceUnselected {
        @Test
        fun `should update the state with the unselected data source and reset the dependent state`() = runBlocking {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
            }

            toolbarModel.unselectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertNull(toolbarState.selectedDataSource)
                // Should also reset the rest of the state dependent on the DataSource selection
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
                assertEquals(toolbarState.selectedDataSourceConnectionFailed, false)
                assertEquals(toolbarState.selectedDatabase, null)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databasesLoadingFailedForSelectedDataSource, false)
                assertEquals(toolbarState.databases, emptyList<String>())
            }

            verify(toolbarSettings).dataSourceId = null
            verify(toolbarSettings, times(2)).database = null
            verify(editorService).detachDataSourceFromSelectedEditor(dataSource)
            verify(editorService, times(2)).reAnalyzeSelectedEditor(applyReadAction = true)
        }
    }

    @Nested
    @DisplayName("when a DataSource is removed")
    inner class DataSourceRemoved {
        @Test
        fun `should update the state with the unselected data source and reset the dependent state`() = runBlocking {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
            }

            toolbarModel.dataSourceRemoved(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertNull(toolbarState.selectedDataSource)
                // Should also reset the rest of the state dependent on the DataSource selection
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
                assertEquals(toolbarState.selectedDataSourceConnectionFailed, false)
                assertEquals(toolbarState.selectedDatabase, null)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databasesLoadingFailedForSelectedDataSource, false)
                assertEquals(toolbarState.databases, emptyList<String>())
            }

            verify(toolbarSettings).dataSourceId = null
            verify(toolbarSettings, times(2)).database = null
            verify(editorService).detachDataSourceFromSelectedEditor(dataSource)
            verify(editorService, times(2)).reAnalyzeSelectedEditor(applyReadAction = true)
        }
    }

    @Nested
    @DisplayName("when a DataSource is terminated")
    inner class DataSourceTerminated {
        @Test
        fun `should update the state with the unselected data source and reset the dependent state`() = runBlocking {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
            }

            toolbarModel.dataSourceTerminated(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertNull(toolbarState.selectedDataSource)
                // Should also reset the rest of the state dependent on the DataSource selection
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
                assertEquals(toolbarState.selectedDataSourceConnectionFailed, false)
                assertEquals(toolbarState.selectedDatabase, null)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databasesLoadingFailedForSelectedDataSource, false)
                assertEquals(toolbarState.databases, emptyList<String>())
            }

            verify(toolbarSettings).dataSourceId = null
            verify(toolbarSettings, times(2)).database = null
            verify(editorService).detachDataSourceFromSelectedEditor(dataSource)
            verify(editorService, times(2)).reAnalyzeSelectedEditor(applyReadAction = true)
        }
    }

    @Nested
    @DisplayName("when a DataSource is changed")
    inner class DataSourceChanged {
        @Test
        fun `should update the state with the updated data source`() {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
            }

            val newDataSource = mockDataSource()
            `when`(newDataSource.uniqueId).thenReturn("mocked-data-source-unique-id")
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(newDataSource))
            toolbarModel.dataSourcesChanged()
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                assertEquals(toolbarState.dataSources, listOf(newDataSource))
            }
        }
    }

    @Nested
    @DisplayName("when a database is selected")
    inner class DatabaseSelected {
        @Test
        fun `should update the state with the selected database`() = runBlocking {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                // Should also reset the rest of the state dependent on the DataSource selection
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
                assertEquals(toolbarState.selectedDataSourceConnectionFailed, false)
                assertEquals(toolbarState.selectedDatabase, null)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databasesLoadingFailedForSelectedDataSource, false)
                assertEquals(toolbarState.databases, emptyList<String>())
            }

            toolbarModel.databasesLoadingSuccessful(dataSource, listOf("mongodb", "bongodb"))
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                // Should also reset the rest of the state dependent on the DataSource selection
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
                assertEquals(toolbarState.selectedDataSourceConnectionFailed, false)
                assertEquals(toolbarState.selectedDatabase, null)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databasesLoadingFailedForSelectedDataSource, false)
                assertEquals(toolbarState.databases, listOf("mongodb", "bongodb"))
            }

            toolbarModel.selectDatabase("mongodb")
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                // Should also reset the rest of the state dependent on the DataSource selection
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
                assertEquals(toolbarState.selectedDataSourceConnectionFailed, false)
                assertEquals(toolbarState.selectedDatabase, "mongodb")
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databasesLoadingFailedForSelectedDataSource, false)
                assertEquals(toolbarState.databases, listOf("mongodb", "bongodb"))
            }

            verify(toolbarSettings, times(1)).database = "mongodb"
            verify(editorService).attachDatabaseToSelectedEditor("mongodb")
        }
    }

    @Nested
    @DisplayName("when a database is unselected")
    inner class DatabaseUnSelected {
        @Test
        fun `should update the state with the unselected database`() = runBlocking {
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            toolbarModel.initialise()
            toolbarModel.selectDataSource(dataSource)
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                // Should also reset the rest of the state dependent on the DataSource selection
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
                assertEquals(toolbarState.selectedDataSourceConnectionFailed, false)
                assertEquals(toolbarState.selectedDatabase, null)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databasesLoadingFailedForSelectedDataSource, false)
                assertEquals(toolbarState.databases, emptyList<String>())
            }

            toolbarModel.databasesLoadingSuccessful(dataSource, listOf("mongodb", "bongodb"))
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                // Should also reset the rest of the state dependent on the DataSource selection
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
                assertEquals(toolbarState.selectedDataSourceConnectionFailed, false)
                assertEquals(toolbarState.selectedDatabase, null)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databasesLoadingFailedForSelectedDataSource, false)
                assertEquals(toolbarState.databases, listOf("mongodb", "bongodb"))
            }

            toolbarModel.selectDatabase("mongodb")
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                // Should also reset the rest of the state dependent on the DataSource selection
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
                assertEquals(toolbarState.selectedDataSourceConnectionFailed, false)
                assertEquals(toolbarState.selectedDatabase, "mongodb")
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databasesLoadingFailedForSelectedDataSource, false)
                assertEquals(toolbarState.databases, listOf("mongodb", "bongodb"))
            }

            verify(toolbarSettings, times(1)).database = "mongodb"
            verify(editorService).attachDatabaseToSelectedEditor("mongodb")

            toolbarModel.unselectDatabase("mongodb")
            eventually {
                val toolbarState = toolbarModel.toolbarState.value
                assertEquals(toolbarState.selectedDataSource?.uniqueId, dataSource.uniqueId)
                // Should also reset the rest of the state dependent on the DataSource selection
                assertEquals(toolbarState.selectedDataSourceConnecting, false)
                assertEquals(toolbarState.selectedDataSourceConnectionFailed, false)
                assertEquals(toolbarState.selectedDatabase, null)
                assertEquals(toolbarState.databasesLoadingForSelectedDataSource, false)
                assertEquals(toolbarState.databasesLoadingFailedForSelectedDataSource, false)
                assertEquals(toolbarState.databases, listOf("mongodb", "bongodb"))
            }

            verify(toolbarSettings, times(2)).database = null
            verify(editorService).detachDatabaseFromSelectedEditor("mongodb")
        }
    }
}
