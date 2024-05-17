package com.mongodb.jbplugin.observability.actions

import com.intellij.database.dataSource.localDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.Service
import com.intellij.openapi.rd.util.launchChildOnUi
import com.intellij.openapi.ui.Messages
import com.mongodb.jbplugin.dataaccess.ServerInfoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class GetMongoDBVersionActionService(
    private val coroutineScope: CoroutineScope
) {
    fun actionPerformed(event: AnActionEvent) {
        coroutineScope.launch {
            val repository = event.project!!.getService(ServerInfoRepository::class.java)
            val dataSource = event.dataContext.getData(PlatformDataKeys.PSI_ELEMENT) as DbDataSource

            val buildInfo = repository.getServerInfo(dataSource.localDataSource!!)

            coroutineScope.launchChildOnUi {
                Messages.showMessageDialog(buildInfo.version, "Show DB Version", null)
            }
        }
    }
}

class GetMongoDBVersionAction: AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.project!!.getService(GetMongoDBVersionActionService::class.java).actionPerformed(event)
    }
}