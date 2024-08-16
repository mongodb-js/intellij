package com.mongodb.jbplugin.inspections.impl

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.editor.MdbJavaEditorToolbar
import com.mongodb.jbplugin.i18n.InspectionsAndInlaysMessages
import com.mongodb.jbplugin.inspections.MongoDbInspection
import com.mongodb.jbplugin.linting.FieldCheckWarning
import com.mongodb.jbplugin.linting.FieldCheckingLinter
import com.mongodb.jbplugin.mql.Node

/**
 * This inspection object calls the linting engine and transforms the result so they can be rendered in the IntelliJ
 * editor.
 */
object FieldCheckLinterInspection : MongoDbInspection {
    override fun visitMongoDbQuery(
        dataSource: LocalDataSource?,
        problems: ProblemsHolder,
        query: Node<PsiElement>,
    ) {
        if (dataSource == null || !dataSource.isConnected()) {
            return registerNoConnectionProblem(problems, query.source)
        }

        val readModelProvider = query.source.project.getService(DataGripBasedReadModelProvider::class.java)

        val result =
            FieldCheckingLinter.lintQuery(
                dataSource,
                readModelProvider,
                query,
            )

        result.warnings.forEach {
            when (it) {
                is FieldCheckWarning.FieldDoesNotExist -> registerFieldDoesNotExistProblem(problems, it)
            }
        }
    }

    private fun registerNoConnectionProblem(problems: ProblemsHolder, source: PsiElement) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.field.checking.error.message.no.connection",
        )
        if (problems.results.none { it.descriptionTemplate == problemDescription }) {
            problems.registerProblem(
                source,
                problemDescription,
                ProblemHighlightType.WARNING,
                OpenConnectionChooserQuickFix(
                    InspectionsAndInlaysMessages.message("inspection.field.checking.quickfix.choose.new.connection"),
                ),
            )
        }
    }

    private fun registerFieldDoesNotExistProblem(
        problems: ProblemsHolder,
        warningInfo: FieldCheckWarning.FieldDoesNotExist<PsiElement>,
    ) {
        problems.registerProblem(
            warningInfo.source,
            InspectionsAndInlaysMessages.message(
                "inspection.field.checking.error.message",
                warningInfo.field,
                warningInfo.namespace,
            ),
            ProblemHighlightType.WARNING,
            OpenConnectionChooserQuickFix(
                InspectionsAndInlaysMessages.message("inspection.field.checking.quickfix.choose.new.connection"),
            ),
        )
    }

    /**
     * This quickfix opens a modal with the connection chooser.
     *
     * @param message
     */
    private class OpenConnectionChooserQuickFix(
        private val message: String,
    ) : LocalQuickFix {
        override fun getFamilyName(): String = message

        override fun applyFix(
            project: Project,
            descriptor: ProblemDescriptor,
        ) {
            val editor = FileEditorManager.getInstance(project).selectedTextEditorWithRemotes[0] ?: return
            MdbJavaEditorToolbar.showModalForSelection(editor, project.coroutineScope)
        }
    }
}
