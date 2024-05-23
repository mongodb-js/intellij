package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import org.bson.Document
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito

import kotlinx.coroutines.runBlocking

class BuildInfoTest {
    @Test
    fun `returns a valid build info`(): Unit = runBlocking {
        val command = Document(mapOf("buildInfo" to 1))
        val driver = Mockito.mock<MongoDbDriver>()
        Mockito.`when`(driver.runCommand(command, BuildInfo::class)).thenReturn(BuildInfo("7.8.0", "1235abc"))

        val data = BuildInfo.Slice.queryUsingDriver(driver)
        Assertions.assertEquals("7.8.0", data.version)
        Assertions.assertEquals("1235abc", data.gitVersion)
    }
}