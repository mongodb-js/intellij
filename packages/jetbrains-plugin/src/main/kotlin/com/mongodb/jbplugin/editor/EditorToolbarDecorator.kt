package com.mongodb.jbplugin.editor

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.util.messages.MessageBusConnection

/**
 * This decorator listens to an IntelliJ Editor lifecycle
 * and attaches our toolbar if necessary.
 */
class EditorToolbarDecorator : EditorFactoryListener {
    private val toolbar = MdbJavaEditorToolbar()
    private lateinit var connection: MessageBusConnection
    private lateinit var editor: Editor

    override fun editorCreated(event: EditorFactoryEvent) {
        editor = event.editor

        ensureToolbarIsVisibleIfNecessary()
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        Disposer.dispose(connection)
    }

    private fun ensureToolbarIsVisibleIfNecessary() {
        if (!editor.hasHeaderComponent()) {
            if (isEditingJavaFileWithMongoDBRelatedCode()) {
                if (editor is EditorEx) {
                    editor.permanentHeaderComponent = toolbar
                }
                editor.headerComponent = toolbar
            }
        } else {
            if (!isEditingJavaFileWithMongoDBRelatedCode()) {
                if (editor is EditorEx) {
                    editor.permanentHeaderComponent = null
                }
                editor.headerComponent = null
            }
        }
    }

    private fun isEditingJavaFileWithMongoDbrelatedCode(): Boolean {
        val project = editor.project ?: return false
        val psiFile = PsiManager.getInstance(project).findFile(editor.virtualFile) ?: return false
        if (psiFile.language != JavaLanguage.INSTANCE) {
            return false
        }

        val javaPsiFile = psiFile as PsiJavaFile
        return arrayOf(
            this::isUsingTheJavaDriver,
        ).any { it(javaPsiFile) }
    }

    private fun isUsingTheJavaDriver(psiFile: PsiJavaFile): Boolean {
        val importStatements = psiFile.importList?.allImportStatements ?: emptyArray()
        return importStatements.any {
            return@any it.importReference?.canonicalText?.startsWith("com.mongodb") == true
        }
    }
}
