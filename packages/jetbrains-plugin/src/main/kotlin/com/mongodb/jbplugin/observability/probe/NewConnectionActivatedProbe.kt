package com.mongodb.jbplugin.observability.probe

import com.intellij.database.console.client.VisibleDatabaseSessionClient
import com.intellij.database.console.session.DatabaseSession
import com.intellij.database.console.session.DatabaseSessionStateListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isMongoDbDataSource
import com.mongodb.jbplugin.accessadapter.slice.BuildInfo
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService
import com.mongodb.jbplugin.observability.useLogMessage

private val logger: Logger = logger<NewConnectionActivatedProbe>()

/** This probe is emitted when a new connection happens through DataGrip.
 */
class NewConnectionActivatedProbe : DatabaseSessionStateListener {
    override fun clientAttached(client: VisibleDatabaseSessionClient) {
    }

    override fun clientDetached(client: VisibleDatabaseSessionClient) {
    }

    override fun clientReattached(
        client: VisibleDatabaseSessionClient,
        source: DatabaseSession,
        target: DatabaseSession,
    ) {
    }

    override fun renamed(session: DatabaseSession) {
    }

    override fun connected(session: DatabaseSession) {
        val application = ApplicationManager.getApplication()
        val telemetryService = application.getService(TelemetryService::class.java)

        val readModelProvider = session.project.getService(
            DataGripBasedReadModelProvider::class.java
        )
        val dataSource = session.connectionPoint.dataSource

        if (!dataSource.isMongoDbDataSource()) {
            return
        }

        val serverInfo = readModelProvider.slice(dataSource, BuildInfo.Slice)

        val newConnectionEvent =
            TelemetryEvent.NewConnection(
                isAtlas = serverInfo.isAtlas,
                isLocalAtlas = serverInfo.isLocalAtlas,
                isLocalhost = serverInfo.isLocalhost,
                isEnterprise = serverInfo.isEnterprise,
                isGenuine = serverInfo.isGenuineMongoDb,
                nonGenuineServerName = serverInfo.nonGenuineVariant,
                serverOsFamily = serverInfo.buildEnvironment["target_os"],
                atlasHost = serverInfo.atlasHost,
                version = serverInfo.version,
            )

        telemetryService.sendEvent(newConnectionEvent)

        logger.info(
            useLogMessage("New connection activated")
                .mergeTelemetryEventProperties(newConnectionEvent)
                .build(),
        )
    }

    override fun disconnected(session: DatabaseSession) {
    }

    override fun stateChanged(event: DatabaseSessionStateListener.ChangeEvent) {
    }
}
