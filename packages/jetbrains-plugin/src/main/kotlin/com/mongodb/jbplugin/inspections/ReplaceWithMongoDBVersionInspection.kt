package com.mongodb.jbplugin.inspections

import com.intellij.codeInspection.*
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.BuildInfo

class ReplaceWithMongoDBVersionInspection: AbstractBaseJavaLocalInspectionTool() {
    class Quickfix(private val dataSource: LocalDataSource): LocalQuickFix {
        override fun getFamilyName(): String {
            return "Replace constant with MongoDB Version"
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            // here we need to query MongoDB and modify the AST of the code
        }

    }

    private fun getActiveConnection(): LocalDataSource? {
        val connections = DatabaseConnectionManager.getInstance().activeConnections
        return connections.firstOrNull { it.connectionPoint.dataSource.databaseDriver?.id?.lowercase() == "mongo" }?.connectionPoint?.dataSource
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        // here we need to detect our string!
    }
}