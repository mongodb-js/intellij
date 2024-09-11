package com.mongodb.jbplugin.editor

import com.intellij.codeInsight.daemon.impl.EditorTracker
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

object DatagripConsoleEditor {
    fun openConsoleForDataSource(project: Project, dataSource: LocalDataSource): Editor? {
        if (!dataSource.isConnected()) {
            return null
        }

        var activeEditor = allConsoleEditorsForDataSource(project, dataSource).firstOrNull()

        activeEditor?.let {
            val currentFile = PsiDocumentManager.getInstance(project).getPsiFile(it.document)!!.virtualFile
            FileEditorManager.getInstance(project).openFile(currentFile, true)
        } ?: run {
            activeEditor = openNewEmptyEditorForDataSource(project, dataSource)
        }

        return activeEditor
    }

    private fun allConsoleEditorsForDataSource(project: Project, dataSource: LocalDataSource): List<Editor> =
        EditorTracker.getInstance(project).activeEditors.filter {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(it.document)
            val virtualFile = psiFile?.virtualFile ?: return@filter false
            val dataSourceOfFile = DbVFSUtils.getDataSource(project, virtualFile)
            dataSource == dataSourceOfFile
        }

    private fun openNewEmptyEditorForDataSource(project: Project, dataSource: LocalDataSource): Editor? {
        val file = DbUIUtil.openInConsole(project, dataSource, null, "", true)!!
        val psiFile = PsiManager.getInstance(project).findFile(file)!!
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
        return EditorFactory.getInstance().getEditors(document, project).firstOrNull()
    }

    fun Editor.appendText(text: String) {
        val document = document
        val textLength = document.textLength
        if (textLength > 0 && document.charsSequence[textLength - 1] != '\n') {
            WriteCommandAction.runWriteCommandAction(project, null, null,
                { document.insertString(textLength, "\n") })
        }

        caretModel.moveToOffset(document.textLength)
        WriteCommandAction.runWriteCommandAction(project, null, null,
            {
                document.insertString(document.textLength, text + "\n")
            })
    }
}