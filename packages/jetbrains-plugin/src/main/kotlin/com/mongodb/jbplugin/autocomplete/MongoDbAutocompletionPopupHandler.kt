package com.mongodb.jbplugin.autocomplete

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.database.dialects.base.endOffset
import com.intellij.database.dialects.base.startOffset
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchChildBackground
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaToken
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.parentOfType
import com.intellij.util.ThreeState
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.editor.CachedQueryService
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasChildren
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

class MongoDbAutocompletionPopupHandler(
    private val coroutineScope: CoroutineScope
) : TypedHandlerDelegate() {
    private data class AutocompletionEvent(
        val file: PsiFile,
        val editor: Editor
    )

    private val events = MutableSharedFlow<AutocompletionEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        coroutineScope.launchChildBackground {
            while (true) {
                events.collectLatest { event ->
                    if (event.file.dataSource == null || event.file.dataSource?.isConnected() == false) {
                        return@collectLatest
                    }

                    readAction {
                        val elementAtCaret = event.file.findElementAt(event.editor.caretModel.offset)?.originalElement
                            ?: return@readAction
                        val methodAtCaret = elementAtCaret.parentOfType<PsiMethod>() ?: return@readAction
                        val queryService = event.file.project.getService(CachedQueryService::class.java)
                        val query = queryService.queryAt(methodAtCaret, forceParsing = true) ?: return@readAction

                        if (isElementCandidateForCompletion(elementAtCaret, query)) {
                            ApplicationManager.getApplication().invokeLater {
                                val autoPopupController = AutoPopupController.getInstance(event.editor.project!!)
                                autoPopupController.cancelAllRequests()
                                autoPopupController.scheduleAutoPopup(event.editor, CompletionType.BASIC, null)
                            }
                        }
                    }
                }
            }
        }

    }

    override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
        if (c == '"') {
            coroutineScope.launchChildBackground {
                events.emit(AutocompletionEvent(file, editor))
            }
        }

        return Result.CONTINUE
    }
}

class MongoDbStringCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
        if (contextElement is PsiJavaToken) {
            return ThreeState.NO
        }

        return ThreeState.YES
    }
}

private fun isElementCandidateForCompletion(element: PsiElement, query: Node<PsiElement>): Boolean {
    val children = query.component<HasChildren<PsiElement>>()?.children ?: emptyList()

    return when (val hasFieldRef = query.component<HasFieldReference<PsiElement>>()?.reference) {
        is HasFieldReference.Known -> areInTheSamePosition(element, hasFieldRef.source)
        is HasFieldReference.Unknown -> {
            areInTheSamePosition(element, hasFieldRef.source)
        }
        else -> when (val collectionRef = query.component<HasCollectionReference<PsiElement>>()?.reference) {
            is HasCollectionReference.Known ->
                areInTheSamePosition(element, collectionRef.databaseSource) ||
                    areInTheSamePosition(element, collectionRef.collectionSource)
            is HasCollectionReference.OnlyCollection ->
                areInTheSamePosition(element, collectionRef.collectionSource)
            is HasCollectionReference.Unknown ->
                areInTheSamePosition(element, collectionRef.databaseSource) ||
                        areInTheSamePosition(element, collectionRef.collectionSource)
            else -> false
        }
    } || children.any { isElementCandidateForCompletion(element, it) }
}

private fun areInTheSamePosition(currentElement: PsiElement, queryElement: PsiElement?): Boolean {
    queryElement ?: return true

    return areEqualWithErrorMargin(currentElement.startOffset, queryElement.startOffset) &&
            areEqualWithErrorMargin(currentElement.endOffset, queryElement.endOffset)
}

// sometimes the position is not the same due to when this is triggered (text might be shifted)
// so we need an error margin
private const val offsetMargin = 5

private fun areEqualWithErrorMargin(first: Int, second: Int): Boolean {
    return abs(first - second) <= offsetMargin
}