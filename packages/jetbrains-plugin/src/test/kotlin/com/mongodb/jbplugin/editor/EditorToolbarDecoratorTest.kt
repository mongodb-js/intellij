package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.editor.services.MdbPluginDisposable
import com.mongodb.jbplugin.editor.services.implementations.MdbDataSourceService
import com.mongodb.jbplugin.editor.services.implementations.MdbEditorService
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.withMockedService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.spy
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

// Ktlint is reporting LONG_LINE for line numbers that seems completely fine
@Suppress("LONG_LINE")
@IntegrationTest
class EditorToolbarDecoratorTest {
    @Nested
    @DisplayName("when execute is triggered")
    inner class EditorToolbarDecoratorExecute {
        @Test
        fun `initialises toolbar for every execution`(project: Project) = runTest {
            val decorator = spy(EditorToolbarDecorator(TestScope()))
            decorator.execute(project)
            runCurrent()
            val toolbarOne = decorator.getToolbarForTests()
            assertTrue(toolbarOne is MdbJavaEditorToolbar)

            decorator.execute(project)
            runCurrent()
            val toolbarTwo = decorator.getToolbarForTests()
            assertTrue(toolbarOne is MdbJavaEditorToolbar)
            assertTrue(toolbarOne != toolbarTwo)
        }

        @Test
        fun `sets up project subscriptions with disposable service for every execution`(project: Project) = runTest {
            val pluginDisposable = mock<MdbPluginDisposable>()
            project.withMockedService(pluginDisposable)

            val decorator = spy(EditorToolbarDecorator(TestScope()))
            decorator.execute(project)
            runCurrent()
            verify(decorator, times(1)).setupSubscriptionsForProject(project)

            decorator.execute(project)
            runCurrent()
            verify(decorator, times(2)).setupSubscriptionsForProject(project)
        }

        @Test
        fun `toggles toolbar using EditorService`(project: Project) = runTest {
            val decorator = spy(EditorToolbarDecorator(TestScope()))
            val editorService = mock<MdbEditorService>()
            project.withMockedService(editorService)
            decorator.execute(project)
            runCurrent()

            verify(editorService, times(1)).toggleToolbarForSelectedEditor(
                decorator.getToolbarForTests()!!
            )
        }

        @Test
        fun `dispatches activity started for other listeners to also trigger their work`(project: Project) = runTest {
            val decorator = spy(EditorToolbarDecorator(TestScope()))
            decorator.execute(project)
            runCurrent()

            verify(decorator, times(1)).dispatchActivityStarted()
        }
    }

    @Nested
    @DisplayName("when selectionChanged is triggered")
    inner class EditorToolbarDecoratorSelectionChanged {
        @Test
        fun `waits for activity to start before doing anything`(project: Project) = runTest {
            val decorator = spy(EditorToolbarDecorator(TestScope()))
            val changeEvent = mock<FileEditorManagerEvent>()
            decorator.selectionChanged(changeEvent)

            assertEquals(decorator.onActivityStartedListeners.size, 1)

            decorator.execute(project)
            runCurrent()

            assertEquals(decorator.onActivityStartedListeners.size, 0)
        }

        @Test
        fun `toggles toolbar using EditorService`(project: Project) = runTest {
            val decorator = spy(EditorToolbarDecorator(TestScope()))
            val editorService = mock<MdbEditorService>()
            project.withMockedService(editorService)
            decorator.execute(project)

            val changeEvent = mock<FileEditorManagerEvent>()
            decorator.selectionChanged(changeEvent)
            runCurrent()

            // First from execute and second from selectionChanged
            verify(editorService, times(2)).toggleToolbarForSelectedEditor(
                decorator.getToolbarForTests()!!
            )
        }
    }

    @Nested
    @DisplayName("when modificationCountChanged is triggered")
    inner class EditorToolbarDecoratorModificationCountChanged {
        @Test
        fun `waits for activity to start before doing anything`(project: Project) = runTest {
            val decorator = spy(EditorToolbarDecorator(TestScope()))
            decorator.modificationCountChanged()

            assertEquals(decorator.onActivityStartedListeners.size, 1)

            decorator.execute(project)
            runCurrent()

            assertEquals(decorator.onActivityStartedListeners.size, 0)
        }

        @Test
        fun `toggles toolbar using EditorService`(project: Project) = runTest {
            val decorator = spy(EditorToolbarDecorator(TestScope()))
            val editorService = mock<MdbEditorService>()
            project.withMockedService(editorService)
            decorator.execute(project)

            decorator.modificationCountChanged()
            runCurrent()

            // First from execute and second from modificationCountChanged
            verify(editorService, times(2)).toggleToolbarForSelectedEditor(
                decorator.getToolbarForTests()!!
            )
        }
    }

    @Nested
    @DisplayName("when dataSourceAdded is triggered")
    inner class EditorToolbarDecoratorDataSourceAdded {
        @Test
        fun `waits for activity to start before doing anything`(project: Project) = runTest {
            // Mocks for our assertions
            val dataSource = mock<LocalDataSource>()
            val dataSourceManager = mock<LocalDataSourceManager>()
            val decorator = spy(EditorToolbarDecorator(TestScope()))
            decorator.dataSourceAdded(dataSourceManager, dataSource)

            assertEquals(decorator.onActivityStartedListeners.size, 1)

            decorator.execute(project)
            runCurrent()

            assertEquals(decorator.onActivityStartedListeners.size, 0)
        }

        @Test
        fun `refreshes the toolbar with the added DataSource`(
            project: Project
        ) = runTest {
            val decorator = EditorToolbarDecorator(TestScope())
            val dataSourceService = mock<MdbDataSourceService>()
            val editorService = mock<MdbEditorService>()
            project.withMockedService(editorService)
            project.withMockedService(dataSourceService)

            // Mocks for our assertions
            val dataSource = mock<LocalDataSource>()
            val dataSourceManager = mock<LocalDataSourceManager>()

            // Defining an early behaviour of not having anything
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(emptyList())
            decorator.execute(project)
            runCurrent()

            val toolbar = decorator.getToolbarForTests()!!
            assertFalse(toolbar.getToolbarState().dataSources.contains(dataSource))

            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            decorator.dataSourceAdded(dataSourceManager, dataSource)
            runCurrent()

            assertTrue(toolbar.getToolbarState().dataSources.contains(dataSource))
        }

        @Test
        fun `refreshes the toolbar with the added DataSource, while preserving the current selection`(
            project: Project
        ) = runTest {
            val decorator = EditorToolbarDecorator(TestScope())
            val dataSourceService = mock<MdbDataSourceService>()
            val editorService = mock<MdbEditorService>()
            project.withMockedService(editorService)
            project.withMockedService(dataSourceService)

            // Mocks for our assertions
            val existingDataSource = mock<LocalDataSource>()
            val existingDataSources = listOf(existingDataSource)
            val newDataSource = mock<LocalDataSource>()
            val dataSourceManager = mock<LocalDataSourceManager>()

            // Defining an early behaviour of not having anything
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(existingDataSources)
            decorator.execute(project)
            runCurrent()

            val toolbar = decorator.getToolbarForTests()!!
            toolbar.setToolbarState(ToolbarState(existingDataSources, existingDataSource, emptyList(), null))
            runCurrent()

            assertTrue(toolbar.getToolbarState().dataSources.contains(existingDataSource))
            assertEquals(toolbar.getToolbarState().selectedDataSource?.uniqueId, existingDataSource.uniqueId)

            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(existingDataSource, newDataSource))
            decorator.dataSourceAdded(dataSourceManager, newDataSource)
            runCurrent()

            assertTrue(toolbar.getToolbarState().dataSources.contains(newDataSource))
            assertEquals(toolbar.getToolbarState().selectedDataSource?.uniqueId, existingDataSource.uniqueId)
        }
    }

    @Nested
    @DisplayName("when dataSourceChanged is triggered")
    inner class EditorToolbarDecoratorDataSourceChanged {
        @Test
        fun `waits for activity to start before doing anything`(project: Project) = runTest {
            // Mocks for our assertions
            val dataSource = mock<LocalDataSource>()
            val dataSourceManager = mock<LocalDataSourceManager>()
            val decorator = spy(EditorToolbarDecorator(TestScope()))
            decorator.dataSourceChanged(dataSourceManager, dataSource)

            assertEquals(decorator.onActivityStartedListeners.size, 1)

            decorator.execute(project)
            runCurrent()

            assertEquals(decorator.onActivityStartedListeners.size, 0)
        }

        @Test
        fun `refreshes the toolbar with the changed DataSource`(
            project: Project
        ) = runTest {
            val decorator = EditorToolbarDecorator(TestScope())
            val dataSourceService = mock<MdbDataSourceService>()
            val editorService = mock<MdbEditorService>()
            project.withMockedService(editorService)
            project.withMockedService(dataSourceService)

            // Mocks for our assertions
            // Both instances are for same DataSource
            val dataSourceInstanceOne = mock<LocalDataSource>()
            `when`(dataSourceInstanceOne.uniqueId).thenReturn("1FOO")
            val dataSourceInstanceTwo = mock<LocalDataSource>()
            `when`(dataSourceInstanceTwo.uniqueId).thenReturn("1FOO")

            val dataSourceManager = mock<LocalDataSourceManager>()

            // Defining an early behaviour of not having anything
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSourceInstanceOne))
            decorator.execute(project)

            val toolbar = decorator.getToolbarForTests()!!
            assertTrue(toolbar.getToolbarState().dataSources.contains(dataSourceInstanceOne))

            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSourceInstanceTwo))
            decorator.dataSourceChanged(dataSourceManager, dataSourceInstanceTwo)
            assertTrue(toolbar.getToolbarState().dataSources.contains(dataSourceInstanceTwo))
        }

        @Test
        fun `refreshes the toolbar with the changed DataSource and preserves the selection, if the selection itself is the changed DataSource`(
            project: Project
        ) = runTest {
            val decorator = EditorToolbarDecorator(TestScope())
            val dataSourceService = mock<MdbDataSourceService>()
            val editorService = mock<MdbEditorService>()
            project.withMockedService(editorService)
            project.withMockedService(dataSourceService)

            // Mocks for our assertions
            // Both instances are for same DataSource
            val dataSourceInstanceOne = mock<LocalDataSource>()
            `when`(dataSourceInstanceOne.uniqueId).thenReturn("1FOO")
            `when`(dataSourceInstanceOne.name).thenReturn("Instance One")

            val dataSourceInstanceTwo = mock<LocalDataSource>()
            `when`(dataSourceInstanceTwo.uniqueId).thenReturn("1FOO")
            `when`(dataSourceInstanceTwo.name).thenReturn("Instance Two")
            val existingDataSources = listOf(dataSourceInstanceOne)

            val dataSourceManager = mock<LocalDataSourceManager>()

            // Defining an early behaviour of not having anything
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSourceInstanceOne))
            decorator.execute(project)

            val toolbar = decorator.getToolbarForTests()!!
            toolbar.setToolbarState(ToolbarState(existingDataSources, dataSourceInstanceOne, emptyList(), null))
            assertTrue(toolbar.getToolbarState().dataSources.contains(dataSourceInstanceOne))
            assertEquals(toolbar.getToolbarState().selectedDataSource?.uniqueId, dataSourceInstanceOne.uniqueId)

            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSourceInstanceTwo))
            decorator.dataSourceChanged(dataSourceManager, dataSourceInstanceTwo)
            assertTrue(toolbar.getToolbarState().dataSources.contains(dataSourceInstanceTwo))
            assertEquals(toolbar.getToolbarState().selectedDataSource?.uniqueId, dataSourceInstanceTwo.uniqueId)
        }
    }

    @Nested
    @DisplayName("when dataSourceRemoved is triggered")
    inner class EditorToolbarDecoratorDataSourceRemoved {
        @Test
        fun `waits for activity to start before doing anything`(project: Project) = runTest {
            // Mocks for our assertions
            val dataSource = mock<LocalDataSource>()
            val dataSourceManager = mock<LocalDataSourceManager>()
            val decorator = spy(EditorToolbarDecorator(TestScope()))
            decorator.dataSourceRemoved(dataSourceManager, dataSource)

            assertEquals(decorator.onActivityStartedListeners.size, 1)

            decorator.execute(project)
            runCurrent()

            assertEquals(decorator.onActivityStartedListeners.size, 0)
        }

        @Test
        fun `refreshes the toolbar with the removed DataSource`(
            project: Project
        ) = runTest {
            val decorator = EditorToolbarDecorator(TestScope())
            val dataSourceService = mock<MdbDataSourceService>()
            val editorService = mock<MdbEditorService>()
            project.withMockedService(editorService)
            project.withMockedService(dataSourceService)

            // Mocks for our assertions
            val dataSource = mock<LocalDataSource>()
            val dataSourceManager = mock<LocalDataSourceManager>()

            // Defining an early behaviour of not having anything
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            decorator.execute(project)
            runCurrent()

            val toolbar = decorator.getToolbarForTests()!!
            assertTrue(toolbar.getToolbarState().dataSources.contains(dataSource))

            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(emptyList())
            decorator.dataSourceRemoved(dataSourceManager, dataSource)
            runCurrent()

            assertFalse(toolbar.getToolbarState().dataSources.contains(dataSource))
        }

        @Test
        fun `refreshes the toolbar with the removed DataSource and also remove the current selection, if the selection is the removed DataSource`(
            project: Project
        ) = runTest {
            val decorator = EditorToolbarDecorator(TestScope())
            val dataSourceService = mock<MdbDataSourceService>()
            val editorService = mock<MdbEditorService>()
            project.withMockedService(editorService)
            project.withMockedService(dataSourceService)

            // Mocks for our assertions
            val dataSource = mock<LocalDataSource>()
            val existingDataSources = listOf(dataSource)
            val dataSourceManager = mock<LocalDataSourceManager>()

            // Defining an early behaviour of not having anything
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(existingDataSources)
            decorator.execute(project)
            runCurrent()

            val toolbar = decorator.getToolbarForTests()!!
            toolbar.setToolbarState(ToolbarState(existingDataSources, dataSource, emptyList(), null))
            assertTrue(toolbar.getToolbarState().dataSources.contains(dataSource))
            assertEquals(toolbar.getToolbarState().selectedDataSource?.uniqueId, dataSource.uniqueId)

            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(emptyList())
            decorator.dataSourceRemoved(dataSourceManager, dataSource)
            runCurrent()

            assertFalse(toolbar.getToolbarState().dataSources.contains(dataSource))
            assertNull(toolbar.getToolbarState().selectedDataSource)
        }
    }

    @Nested
    @DisplayName("when onTerminated is triggered")
    inner class EditorToolbarDecoratorOnTerminate {
        @Test
        fun `waits for activity to start before doing anything`(project: Project) = runTest {
            // Mocks for our assertions
            val dataSource = mock<LocalDataSource>()
            val decorator = spy(EditorToolbarDecorator(TestScope()))
            decorator.onTerminated(dataSource, null)

            assertEquals(decorator.onActivityStartedListeners.size, 1)

            decorator.execute(project)
            runCurrent()

            assertEquals(decorator.onActivityStartedListeners.size, 0)
        }

        @Test
        fun `refresh the DataSource list with the disconnected DataSource`(
            project: Project
        ) = runTest {
            val decorator = EditorToolbarDecorator(TestScope())
            val dataSourceService = mock<MdbDataSourceService>()
            val editorService = mock<MdbEditorService>()
            project.withMockedService(editorService)
            project.withMockedService(dataSourceService)

            // Mocks for our assertions
            val dataSource = mock<LocalDataSource>()

            // Defining an early behaviour of not having anything
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
            decorator.execute(project)
            runCurrent()

            val toolbar = decorator.getToolbarForTests()!!
            assertTrue(toolbar.getToolbarState().dataSources.contains(dataSource))

            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(emptyList())
            decorator.onTerminated(dataSource, null)
            runCurrent()

            assertTrue(toolbar.getToolbarState().dataSources.contains(dataSource))
        }

        @Test
        fun `refresh the DataSource list with the disconnected DataSource and also remove the current selection, if the selection is the disconnected DataSource`(
            project: Project
        ) = runTest {
            val decorator = EditorToolbarDecorator(TestScope())
            val dataSourceService = mock<MdbDataSourceService>()
            val editorService = mock<MdbEditorService>()
            project.withMockedService(editorService)
            project.withMockedService(dataSourceService)

            // Mocks for our assertions
            val dataSource = mock<LocalDataSource>()
            val existingDataSources = listOf(dataSource)

            // Defining an early behaviour of not having anything
            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(existingDataSources)
            decorator.execute(project)
            runCurrent()

            val toolbar = decorator.getToolbarForTests()!!
            toolbar.setToolbarState(ToolbarState(existingDataSources, dataSource, emptyList(), null))
            assertTrue(toolbar.getToolbarState().dataSources.contains(dataSource))
            assertEquals(toolbar.getToolbarState().selectedDataSource?.uniqueId, dataSource.uniqueId)

            `when`(dataSourceService.listMongoDbDataSources()).thenReturn(emptyList())
            decorator.onTerminated(dataSource, null)
            runCurrent()

            assertTrue(toolbar.getToolbarState().dataSources.contains(dataSource))
            assertNull(toolbar.getToolbarState().selectedDataSource)
        }
    }
}
