/**
 * A Simple, example action, that prints out in a modal popup the version of the
 * connected MongoDB Cluster.
 */

package com.mongodb.jbplugin.actions

import com.intellij.database.dataSource.localDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.Service
import com.intellij.openapi.rd.util.launchChildOnUi
import com.intellij.openapi.ui.Messages
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.BuildInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Service that implements the action.
 *
 * @param coroutineScope
 */
@Service(Service.Level.PROJECT)
class GetMongoDbVersionActionService(
    private val coroutineScope: CoroutineScope
) {
    fun actionPerformed(event: AnActionEvent) {
        coroutineScope.launch {
            val readModelProvider = event.project!!.getService(DataGripBasedReadModelProvider::class.java)
            val dataSource = event.dataContext.getData(PlatformDataKeys.PSI_ELEMENT) as DbDataSource
            val buildInfo = readModelProvider.slice(dataSource.localDataSource!!, BuildInfo.Slice)

            coroutineScope.launchChildOnUi {
                Messages.showMessageDialog(buildInfo.version, "Show DB Version", null)
            }
        }
    }
}

/**
 * Action that can be run within the contextual menu of a connection in the data explorer.
 */
class GetMongoDbVersionAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.project!!.getService(GetMongoDbVersionActionService::class.java).actionPerformed(event)
    }
}