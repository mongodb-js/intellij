package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.openapi.editor.Editor
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.mockDatabaseConnection
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

import kotlinx.coroutines.test.TestScope

@IntegrationTest
class EditorToolbarDecoratorTest {
    @Test
    fun `should refresh the data sources when one is added`() {
        val decorator = EditorToolbarDecorator(TestScope())
        val dataSource = mockDataSource()
        val dataSourceManager = mock<LocalDataSourceManager>()

        assertFalse(decorator.toolbar.dataSources.contains(dataSource))

        `when`(dataSourceManager.dataSources).thenReturn(listOf(dataSource))
        decorator.dataSourceAdded(dataSourceManager, dataSource)
        assertTrue(decorator.toolbar.dataSources.contains(dataSource))
    }

    @Test
    fun `should refresh the data sources when one is changed`() {
        val decorator = EditorToolbarDecorator(TestScope())
        val dataSource = mockDataSource()
        val dataSourceManager = mock<LocalDataSourceManager>()

        assertFalse(decorator.toolbar.dataSources.contains(dataSource))

        `when`(dataSourceManager.dataSources).thenReturn(listOf(dataSource))
        decorator.dataSourceChanged(dataSourceManager, dataSource)
        assertTrue(decorator.toolbar.dataSources.contains(dataSource))
    }

    @Test
    fun `should refresh the data sources when one is removed`() {
        val decorator = EditorToolbarDecorator(TestScope())
        val dataSource = mockDataSource()
        val dataSourceManager = mock<LocalDataSourceManager>()

        assertFalse(decorator.toolbar.dataSources.contains(dataSource))

        `when`(dataSourceManager.dataSources).thenReturn(listOf(dataSource))
        decorator.dataSourceRemoved(dataSourceManager, dataSource)
        assertTrue(decorator.toolbar.dataSources.contains(dataSource))
    }

    @Test
    fun `should remove the selected data source when it is disconnected`() {
        val decorator = EditorToolbarDecorator(TestScope())
        val dataSource = mockDataSource()
        val connection = mockDatabaseConnection(dataSource)
        decorator.editor = mock<Editor>()

        decorator.toolbar.dataSources = listOf(dataSource)
        decorator.toolbar.selectedDataSource = dataSource

        assertEquals(dataSource, decorator.toolbar.selectedDataSource)
        decorator.connectionChanged(connection, true)
        assertNull(decorator.toolbar.selectedDataSource)
        verify(decorator.editor).putUserData(decorator.attachedDataSource, dataSource)
    }
}
