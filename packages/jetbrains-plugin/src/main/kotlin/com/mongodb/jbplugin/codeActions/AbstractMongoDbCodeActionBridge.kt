package com.mongodb.jbplugin.codeActions

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
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
 */
@Suppress("TOO_LONG_FUNCTION")
abstract class AbstractMongoDbCodeActionBridge(
    private val codeAction: MongoDbCodeAction,
) : LineMarkerProvider {
    private val queryKeysByDialect = mutableMapOf<Dialect<PsiElement, Project>, Key<CachedValue<Node<PsiElement>>>>()
    private fun queryKey(dialect: Dialect<PsiElement, Project>) =
        queryKeysByDialect.getOrPut(dialect) {
            Key.create(
                "QueryForDialect${dialect.javaClass.name}"
            )
        }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? {
        return dispatchIfValidMongoDbQuery(element)
    }

    private fun dispatchIfValidMongoDbQuery(expression: PsiElement): LineMarkerInfo<PsiElement>? {
        return ApplicationManager.getApplication().runReadAction<LineMarkerInfo<PsiElement>?> {
            val dialect = expression.containingFile.dialect ?: return@runReadAction null

            val queryKey = queryKey(dialect)

            var cachedValue: CachedValue<Node<PsiElement>>? = null
            if (dialect.parser.isCandidateForQuery(expression)) {
                val attachment = dialect.parser.attachment(expression)
                val psiManager = PsiManager.getInstance(expression.project)
                if (!psiManager.areElementsEquivalent(expression, attachment)) {
                    return@runReadAction null
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
            } else {
                return@runReadAction null
            }

            cachedValue?.let {
                val fileInExpression = PsiTreeUtil.getParentOfType(expression, PsiFile::class.java)
                if (fileInExpression == null || fileInExpression.virtualFile == null) {
                    val result = codeAction.visitMongoDbQuery(null, cachedValue!!.value, dialect.formatter)
                    return@runReadAction result
                } else {
                    val cachedQuery = cachedValue!!.value
                    val dataSource = fileInExpression.dataSource

                    val result = codeAction.visitMongoDbQuery(
                        dataSource?.localDataSource,
                        queryWithCollectionReference(cachedQuery, fileInExpression),
                        dialect.formatter,
                    )
                    return@runReadAction result
                }
            }
        }

    }

    private fun queryWithCollectionReference(query: Node<PsiElement>, psiFile: PsiFile) =
        psiFile.database?.let { query.queryWithOverwrittenDatabase(it) } ?: query
}