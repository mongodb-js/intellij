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
import com.mongodb.jbplugin.inspections.quickfixes.OpenConnectionChooserQuickFix
import com.mongodb.jbplugin.linting.NamespaceCheckWarning
import com.mongodb.jbplugin.linting.NamespaceCheckingLinter
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.CoroutineScope

/**
 * @param coroutineScope
 */
@Suppress("MISSING_KDOC_TOP_LEVEL")
class NamespaceCheckInspectionBridge(coroutineScope: CoroutineScope) :
    AbstractMongoDbInspectionBridge(
        coroutineScope,
        NamespaceCheckingLinterInspection,
    )

/**
 * This inspection object calls the linting engine and transforms the result so they can be rendered in the IntelliJ
 * editor.
 */
internal object NamespaceCheckingLinterInspection : MongoDbInspection {
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
            NamespaceCheckingLinter.lintQuery(
                dataSource,
                readModelProvider,
                query,
            )

        result.warnings.forEach {
            when (it) {
                is NamespaceCheckWarning.NoNamespaceInferred ->
                    registerNoNamespaceInferred(coroutineScope, problems, it.source)
                is NamespaceCheckWarning.CollectionDoesNotExist ->
                    registerCollectionDoesNotExist(coroutineScope, problems, it.source, it.database, it.collection)
                is NamespaceCheckWarning.DatabaseDoesNotExist ->
                    registerDatabaseDoesNotExist(coroutineScope, problems, it.source, it.database)
            }
        }
    }

    private fun registerNoNamespaceInferred(
        coroutineScope: CoroutineScope,
        problems: ProblemsHolder,
        source: PsiElement
    ) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.namespace.checking.error.message",
        )
        problems.registerProblem(
            source,
            problemDescription,
            ProblemHighlightType.WARNING,
            OpenConnectionChooserQuickFix(
                coroutineScope,
                InspectionsAndInlaysMessages.message(
                    "inspection.field.checking.quickfix.choose.new.connection"
                ),
            ),
        )
    }

    private fun registerDatabaseDoesNotExist(
        coroutineScope: CoroutineScope,
        problems: ProblemsHolder,
        source: PsiElement,
        dbName: String,
    ) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.namespace.checking.error.message.database.missing",
            dbName
        )
        problems.registerProblem(
            source,
            problemDescription,
            ProblemHighlightType.WARNING,
            OpenConnectionChooserQuickFix(
                coroutineScope,
                InspectionsAndInlaysMessages.message(
                    "inspection.field.checking.quickfix.choose.new.connection"
                ),
            ),
        )
    }

    private fun registerCollectionDoesNotExist(
        coroutineScope: CoroutineScope,
        problems: ProblemsHolder,
        source: PsiElement,
        dbName: String,
        collName: String
    ) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.namespace.checking.error.message.collection.missing",
            collName,
            dbName
        )

        problems.registerProblem(
            source,
            problemDescription,
            ProblemHighlightType.WARNING,
            OpenConnectionChooserQuickFix(
                coroutineScope,
                InspectionsAndInlaysMessages.message(
                    "inspection.field.checking.quickfix.choose.new.connection"
                ),
            ),
        )
    }
}
