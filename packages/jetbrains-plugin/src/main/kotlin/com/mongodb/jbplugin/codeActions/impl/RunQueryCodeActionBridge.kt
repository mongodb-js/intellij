package com.mongodb.jbplugin.codeActions.impl

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.util.DbUIUtil
import com.intellij.database.vfs.DbVFSUtils
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.codeActions.AbstractMongoDbCodeActionBridge
import com.mongodb.jbplugin.codeActions.MongoDbCodeAction
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.mongosh.MongoshDialect
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.mql.Node
import java.util.*


class RunQueryCodeActionBridge : AbstractMongoDbCodeActionBridge(RunQueryCodeAction)

internal object RunQueryCodeAction: MongoDbCodeAction {
    override fun visitMongoDbQuery(
        dataSource: LocalDataSource?,
        query: Node<PsiElement>,
        formatter: DialectFormatter
    ): LineMarkerInfo<PsiElement>? {
        dataSource ?: return null
        if (!dataSource.isConnected()) {
            return null
        }

        return LineMarkerInfo(
            query.source,
            query.source.textRange,
            Icons.logo,
            {
                "Run query in console"
            },
            {
                event, element -> openQueryInEditor(dataSource, query)
            },
            GutterIconRenderer.Alignment.CENTER
        )
    }

    private fun openQueryInEditor(dataSource: LocalDataSource, query: Node<PsiElement>) {
        val code = MongoshDialect.formatter.formatQuery(query, explain = false)
        val currentProject = query.source.project

        var activeEditor: Optional<Editor?> =
            EditorTracker.getInstance(currentProject).activeEditors.stream().filter { it: Editor ->
                val currentFile = PsiDocumentManager.getInstance(currentProject).getPsiFile(it.document)!!
                    .virtualFile
                val dataSourceOfFile = DbVFSUtils.getDataSource(currentProject, currentFile)
                dataSource == dataSourceOfFile
            }.findFirst()

        if (activeEditor.isPresent) {
            val editor = activeEditor.get()
            val currentFile = PsiDocumentManager.getInstance(currentProject).getPsiFile(editor.document)!!
                .virtualFile
            FileEditorManager.getInstance(currentProject).openFile(currentFile, true)
        } else {
            val vFile = DbUIUtil.openInConsole(currentProject, dataSource, null, "", true)!!
            val psiFile = PsiManager.getInstance(currentProject).findFile(vFile)
            val document = PsiDocumentManager.getInstance(currentProject).getDocument(
                psiFile!!
            )
            activeEditor = Arrays.stream(EditorFactory.getInstance().getEditors(document!!, currentProject)).findFirst()
        }

        if (activeEditor.isEmpty) {
            return
        }

        val editor = activeEditor.get()
        val document = editor.document
        val textLength = document.textLength
        if (textLength > 0 && document.charsSequence[textLength - 1] != '\n') {
            WriteCommandAction.runWriteCommandAction(editor.project, null, null,
                { document.insertString(textLength, "\n") })
        }

        editor.caretModel.moveToOffset(document.textLength)
        WriteCommandAction.runWriteCommandAction(editor.project, null, null,
            {
                document.insertString(document.textLength, code + "\n")
            })

    }

}