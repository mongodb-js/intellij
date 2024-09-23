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
import com.mongodb.jbplugin.linting.FieldCheckWarning
import com.mongodb.jbplugin.linting.FieldCheckingLinter
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import kotlinx.coroutines.CoroutineScope

/**
 * @param coroutineScope
 */
@Suppress("MISSING_KDOC_TOP_LEVEL")
class FieldCheckInspectionBridge(coroutineScope: CoroutineScope) :
    AbstractMongoDbInspectionBridge(
        coroutineScope,
        FieldCheckLinterInspection,
    )

/**
 * This inspection object calls the linting engine and transforms the result so they can be rendered in the IntelliJ
 * editor.
 */
internal object FieldCheckLinterInspection : MongoDbInspection {
    override fun visitMongoDbQuery(
        coroutineScope: CoroutineScope,
        dataSource: LocalDataSource?,
        problems: ProblemsHolder,
        query: Node<PsiElement>,
        formatter: DialectFormatter,
    ) {
        if (dataSource == null || !dataSource.isConnected()) {
            return registerNoConnectionProblem(coroutineScope, problems, query.source)
        }

        if (query.component<HasCollectionReference<PsiElement>>()?.reference is HasCollectionReference.OnlyCollection) {
            return registerNoDatabaseSelectedProblem(coroutineScope, problems, query.source)
        }

        val readModelProvider = query.source.project.getService(
            DataGripBasedReadModelProvider::class.java
        )

        val result =
            FieldCheckingLinter.lintQuery(
                dataSource,
                readModelProvider,
                query,
            )

        result.warnings.forEach {
            when (it) {
                is FieldCheckWarning.FieldDoesNotExist -> registerFieldDoesNotExistProblem(
                    coroutineScope,
                    problems,
                    it
                )
                is FieldCheckWarning.FieldValueTypeMismatch ->
                    registerFieldValueTypeMismatch(coroutineScope, problems, it, formatter)
            }
        }
    }

    private fun registerNoConnectionProblem(
        coroutineScope: CoroutineScope,
        problems: ProblemsHolder,
        source: PsiElement
    ) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.field.checking.error.message.no.connection",
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

    private fun registerNoDatabaseSelectedProblem(
        coroutineScope: CoroutineScope,
        problems: ProblemsHolder,
        source: PsiElement
    ) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.field.checking.error.message.no.database",
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

    private fun registerFieldDoesNotExistProblem(
        coroutineScope: CoroutineScope,
        problems: ProblemsHolder,
        warningInfo: FieldCheckWarning.FieldDoesNotExist<PsiElement>,
    ) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.field.checking.error.message",
            warningInfo.field,
            warningInfo.namespace,
        )
        problems.registerProblem(
            warningInfo.source,
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

    private fun registerFieldValueTypeMismatch(
        coroutineScope: CoroutineScope,
        problems: ProblemsHolder,
        warningInfo: FieldCheckWarning.FieldValueTypeMismatch<PsiElement>,
        formatter: DialectFormatter,
    ) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.field.checking.error.message.value.type.mismatch",
            formatter.formatType(warningInfo.valueType),
            formatter.formatType(warningInfo.fieldType),
            warningInfo.field,
        )
        problems.registerProblem(
            warningInfo.valueSource,
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
