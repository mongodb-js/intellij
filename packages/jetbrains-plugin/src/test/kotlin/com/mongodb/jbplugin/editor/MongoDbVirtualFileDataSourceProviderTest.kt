package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.BasicDataSourceManager
import com.intellij.database.model.RawDataSource
import com.intellij.database.psi.DbDataSourceImpl
import com.intellij.database.psi.DbPsiFacade
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.withMockedService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@IntegrationTest
class MongoDbVirtualFileDataSourceProviderTest {
    @Test
    fun `returns a datasource if attached to a file`(project: Project) {
        val provider = MongoDbVirtualFileDataSourceProvider()
        val facade = mock<DbPsiFacade>()
        project.withMockedService(facade)

        val dataSource = mockDataSource()
        val dataSourceManager = mock<BasicDataSourceManager<RawDataSource>>()
        val file = mock<VirtualFile>()

        `when`(
            file.getUserData(MongoDbVirtualFileDataSourceProvider.Keys.attachedDataSource)
        ).thenReturn(dataSource)
        `when`(facade.findDataSource(dataSource.uniqueId)).thenReturn(
            DbDataSourceImpl(project, dataSource, dataSourceManager),
        )

        assertNotNull(provider.getDataSource(project, file))
    }

    @Test
    fun `returns null if no data source has been attached`(project: Project) {
        val provider = MongoDbVirtualFileDataSourceProvider()
        val facade = mock<DbPsiFacade>()
        project.withMockedService(facade)
        val file = mock<VirtualFile>()

        `when`(
            file.getUserData(MongoDbVirtualFileDataSourceProvider.Keys.attachedDataSource)
        ).thenReturn(null)

        assertNull(provider.getDataSource(project, file))
        verify(facade, never()).findDataSource(any())
    }
}
