/**
 * This inspection is used for index checking. It warns if a query is not using a
 * proper index.
 */

package com.mongodb.jbplugin.inspections.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.mongosh.MongoshDialect
import com.mongodb.jbplugin.i18n.InspectionsAndInlaysMessages
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.inspections.MongoDbInspection
import com.mongodb.jbplugin.inspections.quickfixes.OpenDataSourceConsoleAppendingCode
import com.mongodb.jbplugin.linting.IndexCheckWarning
import com.mongodb.jbplugin.linting.IndexCheckingLinter
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasChildren
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasValueReference
import kotlinx.coroutines.CoroutineScope

/**
 * @param coroutineScope
 */
@Suppress("MISSING_KDOC_TOP_LEVEL")
class IndexCheckInspectionBridge(coroutineScope: CoroutineScope) :
    AbstractMongoDbInspectionBridge(
        coroutineScope,
        IndexCheckLinterInspection,
    )

/**
 * This inspection object calls the linting engine and transforms the result so they can be rendered in the IntelliJ
 * editor.
 */
object IndexCheckLinterInspection : MongoDbInspection {
    internal val enabledIndexWarning = Key.create<Boolean>("Temporary.EnabledIndexWarning")

    fun enableForFile(file: PsiFile) {
        file.putUserData(enabledIndexWarning, true)
        DaemonCodeAnalyzer.getInstance(file.project).restart(file)
    }

    fun isEnabledForFile(file: PsiFile): Boolean {
        return file.getUserData(enabledIndexWarning) == true
    }

    fun disableForFile(file: PsiFile) {
        file.removeUserData(enabledIndexWarning)
    }

    override fun visitMongoDbQuery(
        coroutineScope: CoroutineScope,
        dataSource: LocalDataSource?,
        problems: ProblemsHolder,
        query: Node<PsiElement>,
        formatter: DialectFormatter,
    ) {
        if (dataSource == null || !dataSource.isConnected()) {
            return
        }

        val sourceFile = query.source.containingFile!!
        if (!isEnabledForFile(sourceFile)) {
            return
        }

        if (query.allFieldsInQuery() != setOf("dispute.status", "dispute.type")) {
            return
        }

        val readModelProvider = query.source.project.getService(DataGripBasedReadModelProvider::class.java)

        val result =
            IndexCheckingLinter.lintQuery(
                dataSource,
                readModelProvider,
                query,
            )

        result.warnings.forEach {
            when (it) {
                is IndexCheckWarning.QueryNotCoveredByIndex ->
                    registerQueryNotCoveredByIndex(coroutineScope, dataSource, problems, query)
            }
        }
    }

    private fun registerQueryNotCoveredByIndex(
        coroutineScope: CoroutineScope,
        localDataSource: LocalDataSource,
        problems: ProblemsHolder,
        query: Node<PsiElement>
    ) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.index.checking.error.query.not.covered.by.index",
        )

        problems.registerProblem(
            query.source,
            problemDescription,
            ProblemHighlightType.WARNING,
            OpenDataSourceConsoleAppendingCode(
                coroutineScope,
                InspectionsAndInlaysMessages.message(
                    "inspection.index.checking.error.query.not.covered.by.index.quick.fix"
                ),
                localDataSource
            ) {
                val sourceFile = query.source.containingFile!!
                disableForFile(sourceFile)

                MongoshDialect.formatter.indexCommandForQuery(query)
            }
        )
    }
}


private fun <S> Node<S>.allFieldsInQuery(): Set<String?> {
    val hasChildren = component<HasChildren<S>>()
    val otherRefs = hasChildren?.children?.flatMap { it.allFieldsInQuery() }?.toSet() ?: emptySet<String>()
    val fieldRef = component<HasFieldReference<S>>()?.reference ?: return otherRefs
    val valueRef = component<HasValueReference<S>>()?.reference
    return if (fieldRef is HasFieldReference.Known) {
        otherRefs + (valueRef?.let { reference ->
            when (reference) {
                is HasValueReference.Constant<S> -> fieldRef.fieldName
                is HasValueReference.Runtime<S> -> fieldRef.fieldName
                else -> null
            }
        } ?: fieldRef.fieldName)
    } else {
        otherRefs
    }
}
