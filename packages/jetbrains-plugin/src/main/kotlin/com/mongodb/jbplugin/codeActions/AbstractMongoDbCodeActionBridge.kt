package com.mongodb.jbplugin.codeActions

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.database.dataSource.localDataSource
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.*
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.editor.database
import com.mongodb.jbplugin.editor.dialect
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.CoroutineScope

/**
 * Line markers need to be set up in a leaf node of the tree. Use this instead of
 * Node.source when using it to create a marker. If you want to know more about the details
 * of this quirk, take a look at the documentation of LineMarkerProvider.getLineMarkerInfo.
 *
 * We are choosing the first leaf because essentially it's the method reference, so the marker
 * will show in the line where you have the specific query method, not in the parameters or
 * somewhere else.
 *
 * @see com.intellij.codeInsight.daemon.LineMarkerProvider.getLineMarkerInfo
 */
internal val Node<PsiElement>.sourceForMarker: PsiElement
    get() = this.source.firstLeaf()

/**
 * This class is used to connect a MongoDB query action to IntelliJ.
 * It's responsible for getting the dialect of the current file and
 * do the necessary dependency injection to make the query action work.
 *
 * Usually you won't reimplement methods, just create a new empty class
 * that provides the inspection implementation, in the same file.
 *
 * @see com.mongodb.jbplugin.codeActions.impl.RunQueryCodeActionBridge
 *
 * @param codeAction
 * @param coroutineScope
 */
@Suppress("TOO_LONG_FUNCTION")
abstract class AbstractMongoDbCodeActionBridge(
    private val coroutineScope: CoroutineScope,
    private val codeAction: MongoDbCodeAction,
) : LineMarkerProvider {
    private val queryKeysByDialect = mutableMapOf<Dialect<PsiElement, Project>, Key<CachedValue<Node<PsiElement>>>>()
    private fun queryKey(dialect: Dialect<PsiElement, Project>) =
        queryKeysByDialect.getOrPut(dialect) {
            Key.create(
                "QueryForDialect${dialect.javaClass.name}"
            )
        }

    override fun getLineMarkerInfo(element: PsiElement) = dispatchIfValidMongoDbQuery(element)

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
                    return@runReadAction codeAction.visitMongoDbQuery(
                        coroutineScope,
                        dataSource = null,
                        cachedValue!!.value,
                        dialect.formatter
                    )
                } else {
                    val cachedQuery = cachedValue!!.value
                    val dataSource = fileInExpression.dataSource

                    return@runReadAction codeAction.visitMongoDbQuery(
                        coroutineScope,
                        dataSource?.localDataSource,
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