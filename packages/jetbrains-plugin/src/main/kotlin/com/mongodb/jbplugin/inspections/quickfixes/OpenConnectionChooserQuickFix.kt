package com.mongodb.jbplugin.inspections.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.editor.MdbJavaEditorToolbar
import kotlinx.coroutines.CoroutineScope

/**
 * This quickfix opens a modal with the connection chooser.
 *
 * @param message
 * @param coroutineScope
 */
class OpenConnectionChooserQuickFix(
    private val coroutineScope: CoroutineScope,
    private val message: String,
) : LocalQuickFix {
    override fun getFamilyName(): String = message

    override fun applyFix(
        project: Project,
        descriptor: ProblemDescriptor,
    ) {
        MdbJavaEditorToolbar.showModalForSelection(project)
    }
}