package com.mongodb.jbplugin.dataaccess

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlin.time.Duration.Companion.seconds

data class ServerInfo(val version: String)

@Service(Service.Level.PROJECT)
class ServerInfoRepository(
    private val project: Project
) {
    suspend fun getServerInfo(ds: LocalDataSource): ServerInfo {
        val (versionString) = BaseAccessAdapter(project, ds).runQuery<String>(
            """
                db.version()
            """.trimIndent(),
            timeout = 1.seconds
        )

        return ServerInfo(versionString)
    }
}