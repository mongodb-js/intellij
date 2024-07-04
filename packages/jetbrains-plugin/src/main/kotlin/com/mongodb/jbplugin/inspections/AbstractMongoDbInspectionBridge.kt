package com.mongodb.jbplugin.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.mql.Node

typealias CachedQuery = CachedValue<Node<PsiElement>>
typealias QueryCacheKey = Key<CachedQuery>

/**
 * @param dialect
 * @param inspection
 */
abstract class AbstractMongoDbInspectionBridge(
    private val dialect: Dialect<PsiElement>,
    private val inspection: MongoDbInspection,
) : AbstractBaseJavaLocalInspectionTool() {
    private val queryKey: QueryCacheKey =
        Key.create(
            "QueryForDialect${dialect.javaClass.name}",
        )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession,
    ): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                dispatchIfValidMongoDbQuery(expression)
            }

            override fun visitMethod(method: PsiMethod) {
                dispatchIfValidMongoDbQuery(method)
            }

            private fun dispatchIfValidMongoDbQuery(expression: PsiElement) {
                ApplicationManager.getApplication().runReadAction {
                    var cachedValue: CachedQuery? = null
                    if (dialect.parser.canParse(expression)) {
                        val attachment = dialect.parser.attachment(expression)
                        attachment.getUserData(queryKey)?.let {
                            cachedValue = attachment.getUserData(queryKey)!!
                        } ?: run {
                            val parsedAst =
                                CachedValuesManager.getManager(attachment.project).createCachedValue {
                                    val parsedAst = dialect.parser.parse(expression)
                                    CachedValueProvider.Result.create(parsedAst, attachment)
                                }
                            attachment.putUserData(queryKey, parsedAst)
                            cachedValue = parsedAst
                        }
                    }

                    cachedValue?.let {
                        inspection.visitMongoDbQuery(holder, cachedValue!!.value)
                    }
                }
            }
        }
}
