package com.mongodb.jbplugin.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.localDataSource
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.editor.database
import com.mongodb.jbplugin.editor.dialect
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.CoroutineScope

/**
 * This class is used to connect a MongoDB inspection to IntelliJ.
 * It's responsible for getting the dialect of the current file and
 * do the necessary dependency injection to make the inspection work.
 *
 * Usually you won't reimplement methods, just create a new empty class
 * that provides the inspection implementation, in the same file.
 *
 * @see com.mongodb.jbplugin.inspections.impl.FieldCheckInspectionBridge as an example
 *
 * @param inspection
 * @param coroutineScope
 */
@Suppress("TOO_LONG_FUNCTION")
abstract class AbstractMongoDbInspectionBridge(
    private val coroutineScope: CoroutineScope,
    private val inspection: MongoDbInspection,
) : AbstractBaseJavaLocalInspectionTool() {
    private val queryKeysByDialect = mutableMapOf<Dialect<PsiElement, Project>, Key<CachedValue<Node<PsiElement>>>>()
    private fun queryKey(dialect: Dialect<PsiElement, Project>) =
        queryKeysByDialect.getOrPut(dialect) {
            Key.create(
                "QueryForDialect${dialect.javaClass.name}"
            )
        }

    /**
     * Ktlint complains about buildVisitor being longer than 50 lines but because it is just an object implementation
     * it should be fine to keep it like this to favor readability
     */
    @Suppress("TOO_LONG_FUNCTION")
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
                    val dialect = expression.containingFile.dialect ?: return@runReadAction
                    val queryKey = queryKey(dialect)

                    var cachedValue: CachedValue<Node<PsiElement>>? = null
                    if (dialect.parser.isCandidateForQuery(expression)) {
                        val attachment = dialect.parser.attachment(expression)
                        val psiManager = PsiManager.getInstance(expression.project)
                        if (!psiManager.areElementsEquivalent(expression, attachment)) {
                            return@runReadAction
                        }

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
                        val fileInExpression = PsiTreeUtil.getParentOfType(expression, PsiFile::class.java)
                        if (fileInExpression == null || fileInExpression.virtualFile == null) {
                            inspection.visitMongoDbQuery(
                                coroutineScope, null, holder, cachedValue!!.value,
                                dialect.formatter
                            )
                        } else {
                            val cachedQuery = cachedValue!!.value
                            val dataSource = fileInExpression.dataSource

                            inspection.visitMongoDbQuery(
                                coroutineScope,
                                dataSource?.localDataSource,
                                holder,
                                queryWithCollectionReference(cachedQuery, fileInExpression),
                                dialect.formatter,
                            )
                        }
                    }
                }
            }

            private fun queryWithCollectionReference(query: Node<PsiElement>, psiFile: PsiFile) =
                psiFile.database?.let { query.queryWithOverwrittenDatabase(it) } ?: query
        }
}