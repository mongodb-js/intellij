package com.mongodb.jbplugin.inspections.quickfixes

import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.util.DbUIUtil
import com.intellij.database.vfs.DbVFSUtils
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import kotlinx.coroutines.CoroutineScope

/**
 * This quickfix opens a console for the specified data source. If there is already an existing
 * console, opens it, if not, creates a new one.
 *
 * When the console is open, it appends the codeToAppend at the end of the console editor.
 *
 * @param coroutineScope
 * @param message Name of the quick fix.
 * @param dataSource Data Source to open console from.
 * @param codeToAppend Provider of a string with the code to append. It's a function so it's lazily evaluated.
 */
class OpenDataSourceConsoleAppendingCode(
    private val coroutineScope: CoroutineScope,
    private val message: String,
    private val dataSource: LocalDataSource,
    private val codeToAppend: () -> String,
) : LocalQuickFix {
    override fun getFamilyName(): String = message

    override fun applyFix(
        project: Project,
        descriptor: ProblemDescriptor,
    ) {
        openConsoleForDataSource(project)
    }

    private fun openConsoleForDataSource(project: Project) {
        if (!dataSource.isConnected()) {
            return
        }

        var activeEditor = EditorTracker.getInstance(project).activeEditors.firstOrNull { it: Editor ->
            val currentFile = PsiDocumentManager.getInstance(project).getPsiFile(it.document)!!.virtualFile
            val dataSourceOfFile = DbVFSUtils.getDataSource(project, currentFile)
            dataSource == dataSourceOfFile
        }

        activeEditor?.let {
            val currentFile = PsiDocumentManager.getInstance(project).getPsiFile(it.document)!!.virtualFile
            FileEditorManager.getInstance(project).openFile(currentFile, true)
        } ?: run {
            val file = DbUIUtil.openInConsole(project, dataSource, null, "", true)!!
            val psiFile = PsiManager.getInstance(project).findFile(file)!!
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
            activeEditor = EditorFactory.getInstance().getEditors(document, project).firstOrNull()
        }

        val editor = activeEditor ?: return
        val document = editor.document
        val textLength = document.textLength
        if (textLength > 0 && document.charsSequence[textLength - 1] != '\n') {
            WriteCommandAction.runWriteCommandAction(editor.project, null, null,
                { document.insertString(textLength, "\n") })
        }

        editor.caretModel.moveToOffset(document.textLength)
        WriteCommandAction.runWriteCommandAction(editor.project, null, null,
            {
                document.insertString(document.textLength, codeToAppend() + "\n")
            })
    }
}