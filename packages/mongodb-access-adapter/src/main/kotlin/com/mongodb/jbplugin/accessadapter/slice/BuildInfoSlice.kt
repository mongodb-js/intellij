package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.MongoDBDriver
import com.mongodb.jbplugin.accessadapter.Slice
import org.bson.Document

data class BuildInfo(
    val version: String,
    val gitVersion: String
)
data object BuildInfoSlice : Slice<BuildInfo> {
    override suspend fun queryUsingDriver(from: MongoDBDriver): BuildInfo {
        return from.runCommand(
            Document(mapOf(
                "buildInfo" to 1,
            )),
            BuildInfo::class
        )
    }
}