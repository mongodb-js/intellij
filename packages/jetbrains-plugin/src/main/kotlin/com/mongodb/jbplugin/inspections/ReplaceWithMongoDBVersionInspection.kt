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
            val mongodbReadModel = project.getService(DataGripBasedReadModelProvider::class.java)
            val buildInfo = mongodbReadModel.slice(dataSource, BuildInfo.Slice)

            val stringConstant = descriptor.psiElement
            val factory = JavaPsiFacade.getInstance(project).elementFactory

            val versionString = factory.createExpressionFromText("""
                "${buildInfo.version}"
            """.trimIndent(), null)

            stringConstant.replace(versionString)
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
        val dataSource = getActiveConnection() ?: return PsiElementVisitor.EMPTY_VISITOR

        return object : JavaElementVisitor() {
            override fun visitExpression(expression: PsiExpression) {
                if (expression is PsiLiteralExpression) {
                    val typeOfExpression = PsiTypesUtil.getPsiClass(expression.type)
                    if (typeOfExpression?.qualifiedName == "java.lang.String" && expression.value == "MongoDBVersion") {
                        holder.registerProblem(expression, "MongoDBVersion can be replaced with current cluster version.", Quickfix(dataSource))
                    }
                }
            }
        }
    }
}