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
            val currentFile = PsiDocumentManager.getInstance(project).getPsiFile(it.document)?.virtualFile ?: return@filter false
            val dataSourceOfFile = DbVFSUtils.getDataSource(project, currentFile)
            dataSource == dataSourceOfFile
        }

    private fun openNewEmptyEditorForDataSource(project: Project, dataSource: LocalDataSource): Editor? {
        val file = DbUIUtil.openInConsole(project, dataSource, null, "", true)!!
        val psiFile = PsiManager.getInstance(project).findFile(file)!!
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
        return EditorFactory.getInstance().getEditors(document, project).firstOrNull()
    }

    fun Editor.appendText(text: String) {
        WriteCommandAction.runWriteCommandAction(project, null, null,
            {
                document.setText(text)
                caretModel.moveToOffset(document.textLength)
            })
    }
}