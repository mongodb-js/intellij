package com.mongodb.jbplugin.accessadapter.datagrip

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.accessadapter.slice.BuildInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@IntegrationTest
class DataGripBasedReadModelProviderTest {
    @Test
    fun `can query a slice and returns the result`(
        project: Project,
        dataSource: LocalDataSource,
        version: MongoDbVersion
    ) {
        val service = project.getService(DataGripBasedReadModelProvider::class.java)
        val info = service.slice(dataSource, BuildInfo.Slice)

        assertEquals(version.versionString, info.version)
    }
}