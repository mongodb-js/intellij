package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.mockDataSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@IntegrationTest
class EditorToolbarDecoratorTest {
    @Test
    fun `should refresh the data sources when one is added`(
        project: Project,
        testScope: TestScope,
    ) = testScope.runTest {
        val decorator = EditorToolbarDecorator(TestScope())
        val dataSource = mockDataSource()
        val dataSourceManager = mock<LocalDataSourceManager>()
        decorator.execute(project)
        runCurrent()

        val dataSourceCombo = decorator.toolbar.dataSourceComboBox
        assertFalse(dataSourceCombo.dataSources.contains(dataSource))

        `when`(dataSourceManager.dataSources).thenReturn(listOf(dataSource))
        decorator.dataSourceAdded(dataSourceManager, dataSource)
        assertTrue(dataSourceCombo.dataSources.contains(dataSource))
    }

    @Test
    fun `should refresh the data sources when one is changed`(
        project: Project,
        testScope: TestScope,
    ) = testScope.runTest {
        val decorator = EditorToolbarDecorator(TestScope())
        val dataSource = mockDataSource()
        val dataSourceManager = mock<LocalDataSourceManager>()
        decorator.execute(project)
        runCurrent()

        val dataSourceCombo = decorator.toolbar.dataSourceComboBox
        assertFalse(dataSourceCombo.dataSources.contains(dataSource))

        `when`(dataSourceManager.dataSources).thenReturn(listOf(dataSource))
        decorator.dataSourceChanged(dataSourceManager, dataSource)
        assertTrue(dataSourceCombo.dataSources.contains(dataSource))
    }

    @Test
    fun `should refresh the data sources when one is removed`(
        project: Project,
        testScope: TestScope,
    ) = testScope.runTest {
        val decorator = EditorToolbarDecorator(TestScope())
        val dataSource = mockDataSource()
        val dataSourceManager = mock<LocalDataSourceManager>()
        decorator.execute(project)
        runCurrent()
        val dataSourceCombo = decorator.toolbar.dataSourceComboBox
        assertFalse(dataSourceCombo.dataSources.contains(dataSource))

        `when`(dataSourceManager.dataSources).thenReturn(listOf(dataSource))
        decorator.dataSourceRemoved(dataSourceManager, dataSource)
        assertTrue(dataSourceCombo.dataSources.contains(dataSource))
    }

    @Test
    fun `should remove the selected data source when it is disconnected`(
        project: Project,
        testScope: TestScope,
    ) = testScope.runTest {
        val decorator = EditorToolbarDecorator(TestScope())
        val dataSource = mockDataSource()
        decorator.execute(project)
        decorator.toolbar.reloadDataSources(listOf(dataSource))
        runCurrent()
        val dataSourceCombo = decorator.toolbar.dataSourceComboBox.apply {
            selectedDataSource = dataSource
        }

        assertEquals(dataSource, dataSourceCombo.selectedDataSource)
        decorator.onTerminated(dataSource, null)
        assertNull(dataSourceCombo.selectedDataSource)
    }

    @Test
    fun `should attempt connect when the chosen data source is not connected and fails`(
        project: Project,
        testScope: TestScope,
    ) = testScope.runTest {
        val decorator = EditorToolbarDecorator(testScope)
        val dataSource = mockDataSource()
        val virtualFile = mock<VirtualFile>()
        val mockedEditor = mock<Editor>()
        `when`(mockedEditor.virtualFile).thenReturn(virtualFile)
        decorator.execute(project)
        decorator.toolbar.onDataSourceSelected(dataSource)

        runCurrent()

        verify(virtualFile, never()).putUserData(
            MongoDbVirtualFileDataSourceProvider.Keys.attachedDataSource,
            dataSource
        )
    }

    @Test
    fun `when the datasource changes the list of databases is reset`(
        project: Project,
        testScope: TestScope,
    ) = testScope.runTest {
        val decorator = EditorToolbarDecorator(testScope)
        val dataSource = mockDataSource()
        val virtualFile = mock<VirtualFile>()
        val mockedEditor = mock<Editor>()
        `when`(mockedEditor.virtualFile).thenReturn(virtualFile)
        decorator.execute(project)
        decorator.toolbar.onDataSourceSelected(dataSource)

        runCurrent()

        assertEquals(emptyList<String>(), decorator.toolbar.databaseComboBox.databases)
    }
}
