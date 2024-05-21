package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.MongoDBDriver
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class BuildInfoSliceTest {
    @Test
    fun `returns a valid build info`(): Unit = runBlocking {
        val command = Document(mapOf("buildInfo" to 1))
        val driver = Mockito.mock<MongoDBDriver>()
        Mockito.`when`(driver.runCommand(command, BuildInfo::class)).thenReturn(BuildInfo("7.8.0", "1235abc"))

        val data = BuildInfoSlice.queryUsingDriver(driver)
        Assertions.assertEquals("7.8.0", data.version)
        Assertions.assertEquals("1235abc", data.gitVersion)
    }
}