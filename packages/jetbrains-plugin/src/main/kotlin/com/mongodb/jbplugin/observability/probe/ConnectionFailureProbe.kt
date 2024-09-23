package com.mongodb.jbplugin.observability.probe

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.execution.rmi.RemoteObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isMongoDbDataSource
import com.mongodb.jbplugin.accessadapter.slice.BuildInfo
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService
import com.mongodb.jbplugin.observability.useLogMessage
import java.sql.SQLException

private val logger: Logger = logger<NewConnectionActivatedProbe>()

/** This probe is emitted when a connection error happens in DataGrip. It connects
 * directly into DataGrip extension points, so it shouldn't be instantiated directly.
 */
class ConnectionFailureProbe : DatabaseConnectionManager.Listener {
    override fun connectionChanged(
        connection: DatabaseConnection,
        added: Boolean,
    ) {
    }

    override fun connectionFailed(
        project: Project,
        connectionPoint: DatabaseConnectionPoint,
        th: Throwable,
    ) {
        if (!connectionPoint.dataSource.isMongoDbDataSource()) {
            return
        }

        val exception = if (th is SQLException) th.cause else th

        val application = ApplicationManager.getApplication()
        val telemetryService = application.getService(TelemetryService::class.java)

        val readModelProvider = project.getService(DataGripBasedReadModelProvider::class.java)
        val dataSource = connectionPoint.dataSource
        val serverInfo = readModelProvider.slice(dataSource, BuildInfo.Slice)
        val telemetryEvent =
            if (exception is RemoteObject.ForeignException) {
                connectionFailureEvent(extractMongoDbExceptionCode(exception), exception.originalClassName!!,
 serverInfo)
            } else {
                connectionFailureEvent("<unk>", th.javaClass.simpleName, serverInfo)
            }

        telemetryService.sendEvent(telemetryEvent)
        logger.warn(
            useLogMessage("Failure connecting to cluster")
                .put("isMongoDBException", exception is RemoteObject.ForeignException)
                .put("exceptionMessage", th.message ?: "<no-message>")
                .put("stackTrace", th.stackTrace.joinToString())
                .mergeTelemetryEventProperties(telemetryEvent)
                .build(),
        )
    }

    private fun extractMongoDbExceptionCode(exception: RemoteObject.ForeignException): String {
        val originalCause = exception.cause?.message
        val errorCode =
            originalCause?.let {
                Regex("""\d+""").find(originalCause)?.value ?: "<unk>"
            } ?: "-1"
        return errorCode
    }

    private fun connectionFailureEvent(
        errorCode: String,
        errorName: String,
        serverInfo: BuildInfo,
    ) = TelemetryEvent.ConnectionError(
        errorCode = errorCode,
        errorName = errorName,
        isAtlas = serverInfo.isAtlas,
        isLocalhost = serverInfo.isLocalhost,
        isEnterprise = serverInfo.isEnterprise,
        isGenuine = serverInfo.isGenuineMongoDb,
        nonGenuineServerName = serverInfo.nonGenuineVariant,
        serverOsFamily = serverInfo.buildEnvironment["target_os"],
        isLocalAtlas = serverInfo.isLocalAtlas,
        version = serverInfo.version,
    )
}
