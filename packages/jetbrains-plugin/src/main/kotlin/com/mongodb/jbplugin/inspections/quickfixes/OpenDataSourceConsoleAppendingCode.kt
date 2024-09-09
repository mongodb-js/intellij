package com.mongodb.jbplugin.inspections.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.editor.DatagripConsoleEditor
import com.mongodb.jbplugin.editor.DatagripConsoleEditor.appendText
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
        val editor = DatagripConsoleEditor.openConsoleForDataSource(project, dataSource) ?: return
        editor.appendText(codeToAppend())
    }
}