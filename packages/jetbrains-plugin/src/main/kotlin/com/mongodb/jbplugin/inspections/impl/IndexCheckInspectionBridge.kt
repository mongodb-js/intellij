/**
 * This inspection is used for type checking. It also warns if a field is referenced in a
 * query but doesn't exist in the MongoDB schema.
 */

package com.mongodb.jbplugin.inspections.impl

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.i18n.InspectionsAndInlaysMessages
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.inspections.MongoDbInspection
import com.mongodb.jbplugin.inspections.quickfixes.ImplementAnIndexQuickfix
import com.mongodb.jbplugin.linting.IndexCheckWarning
import com.mongodb.jbplugin.linting.IndexCheckingLinter
import com.mongodb.jbplugin.mql.Node
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
internal object IndexCheckLinterInspection : MongoDbInspection {
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
                    registerQueryNotCoveredByIndex(coroutineScope, problems, it.source)
            }
        }
    }

    private fun registerQueryNotCoveredByIndex(
        coroutineScope: CoroutineScope,
        problems: ProblemsHolder,
        source: PsiElement
    ) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.index.checking.error.query.not.covered.by.index",
        )

        problems.registerProblem(
            source,
            problemDescription,
            ProblemHighlightType.WARNING,
            ImplementAnIndexQuickfix(
                coroutineScope,
                InspectionsAndInlaysMessages.message(
                    "inspection.index.checking.error.query.not.covered.by.index.quick.fix"
                )
            )
        )
    }
}
