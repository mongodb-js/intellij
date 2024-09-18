/**
 * Represents the gutter icon that is used to generate a MongoDB query in shell syntax
 * and run it into a Datagrip console.
 */

package com.mongodb.jbplugin.codeActions.impl

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.rd.util.launchChildBackground
import com.intellij.openapi.rd.util.launchChildOnUi
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.codeActions.AbstractMongoDbCodeActionBridge
import com.mongodb.jbplugin.codeActions.MongoDbCodeAction
import com.mongodb.jbplugin.codeActions.sourceForMarker
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.mongosh.MongoshDialect
import com.mongodb.jbplugin.editor.DatagripConsoleEditor
import com.mongodb.jbplugin.editor.DatagripConsoleEditor.appendText
import com.mongodb.jbplugin.editor.MdbJavaEditorToolbar
import com.mongodb.jbplugin.editor.services.ConnectionState
import com.mongodb.jbplugin.i18n.CodeActionsMessages
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.i18n.MdbToolbarMessages
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.useLogMessage
import kotlinx.coroutines.CoroutineScope

private val log = logger<RunQueryCodeActionBridge>()

/**
 * Bridge class that connects our query action with IntelliJ.
 *
 * @param coroutineScope
 */
class RunQueryCodeActionBridge(coroutineScope: CoroutineScope) :
    AbstractMongoDbCodeActionBridge(
    coroutineScope,
        RunQueryCodeAction
)

/**
 * Actual implementation of the code action.
 */
internal object RunQueryCodeAction : MongoDbCodeAction {
    // It's easier to read the switch inline than in different functions
    // as each function would be really simple.
    @Suppress("TOO_LONG_FUNCTION")
    override fun visitMongoDbQuery(
        coroutineScope: CoroutineScope,
        dataSource: LocalDataSource?,
        query: Node<PsiElement>,
        formatter: DialectFormatter
    ): LineMarkerInfo<PsiElement> = LineMarkerInfo(
            query.sourceForMarker,
            query.sourceForMarker.textRange,
            Icons.runQueryGutter,
            { CodeActionsMessages.message("code.action.run.query") },
            { _, _ ->
                coroutineScope.launchChildBackground {
                    val formattedQuery = MongoshDialect.formatter.formatQuery(query, explain = false)
                    coroutineScope.launchChildOnUi {
                        if (dataSource == null || !dataSource.isConnected()) {
                            var notification: Notification? = null

                            MdbJavaEditorToolbar.showModalForSelection(query.source.project) { state, newDataSource ->
                                when (state) {
                                    is ConnectionState.ConnectionFailed -> {
                                        notification?.expire()
                                        log.warn(
                                            useLogMessage("Could not connect to data source.")
                                                .put("dataSourceName", state.failedDataSource.name)
                                                .build()
                                        )
                                    }

                                    ConnectionState.ConnectionStarted -> {
                                        notification = createNotificationBalloon(newDataSource)
                                        notification?.notify(query.source.project)
                                    }

                                    ConnectionState.ConnectionSuccess -> {
                                        notification?.expire()
                                        openDataGripConsole(query, newDataSource, formattedQuery)
                                    }

                                    ConnectionState.ConnectionUnsuccessful -> {
                                        notification?.expire()
                                        log.warn(
                                            useLogMessage("Could not connect to data source.")
                                                .put("dataSourceName", newDataSource.name)
                                                .build()
                                        )
                                    }
                                }
                            }
                        } else {
                            val editor = DatagripConsoleEditor.openConsoleForDataSource(
                                query.source.project,
                                dataSource
                            )
                            editor?.appendText(formattedQuery)
                        }
                    }
                }
            },
            GutterIconRenderer.Alignment.RIGHT,
            { CodeActionsMessages.message("code.action.run.query") }
        )

    private fun openDataGripConsole(
        query: Node<PsiElement>,
        newDataSource: LocalDataSource,
        formattedQuery: String
    ) {
        val editor = DatagripConsoleEditor.openConsoleForDataSource(query.source.project, newDataSource)
        editor?.appendText(formattedQuery)
    }

    private fun createNotificationBalloon(newDataSource: LocalDataSource) =
        NotificationGroupManager.getInstance()
            .getNotificationGroup("com.mongodb.jbplugin.notifications.Connection")
            .createNotification(
                MdbToolbarMessages.message("connection.chooser.notification.title", newDataSource.name),
                MdbToolbarMessages.message("connection.chooser.notification.message"),
                NotificationType.INFORMATION,
            )
}