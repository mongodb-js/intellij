package com.mongodb.jbplugin.editor.services

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.editor.MdbJavaEditorToolbar

/**
 * Interface that outlines helpers to interact with IntelliJ's editors
 */
interface EditorService {
    val inferredDatabase: String?
    val selectedEditor: Editor?
    fun reAnalyzeSelectedEditor(applyReadAction: Boolean)
    fun getDialectForSelectedEditor(): Dialect<PsiElement, Project>?
    fun removeDialectForSelectedEditor()

    fun attachDataSourceToSelectedEditor(dataSource: LocalDataSource)
    fun detachDataSourceFromSelectedEditor(dataSource: LocalDataSource)
    fun attachDatabaseToSelectedEditor(database: String)
    fun detachDatabaseFromSelectedEditor(database: String)

    fun isDatabaseComboBoxVisibleForSelectedEditor(): Boolean
    fun getToolbarFromSelectedEditor(): MdbJavaEditorToolbar?
    fun toggleToolbarForSelectedEditor(toolbar: MdbJavaEditorToolbar, applyReadActionForFileAnalyses: Boolean)
}
