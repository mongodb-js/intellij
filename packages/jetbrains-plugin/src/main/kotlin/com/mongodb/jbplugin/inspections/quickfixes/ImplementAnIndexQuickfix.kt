package com.mongodb.jbplugin.inspections.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

/**
 * This quickfix writes the index creation script.
 *
 * @param message
 * @param coroutineScope
 */
class ImplementAnIndexQuickfix(
    private val coroutineScope: CoroutineScope,
    private val message: String,
) : LocalQuickFix {
    override fun getFamilyName(): String = message

    override fun applyFix(
        project: Project,
        descriptor: ProblemDescriptor,
    ) {

    }
}