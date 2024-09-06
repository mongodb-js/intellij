/**
 * Represents the gutter icon that is used to generate a MongoDB query in shell syntax
 * and run it into a Datagrip console.
 */

package com.mongodb.jbplugin.codeActions.impl

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.codeActions.AbstractMongoDbCodeActionBridge
import com.mongodb.jbplugin.codeActions.MongoDbCodeAction
import com.mongodb.jbplugin.codeActions.sourceForMarker
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.mongosh.MongoshDialect
import com.mongodb.jbplugin.editor.DatagripConsoleEditor
import com.mongodb.jbplugin.editor.DatagripConsoleEditor.appendText
import com.mongodb.jbplugin.i18n.CodeActionsMessages
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.CoroutineScope

/**
 * Bridge class that connects our query action with IntelliJ.
 *
 * @param coroutineScope
 */
class RunQueryCodeActionBridge(coroutineScope: CoroutineScope) :
    AbstractMongoDbCodeActionBridge(
    coroutineScope,
        RunQueryCodeAction
)

/**
 * Actual implementation of the code action.
 */
internal object RunQueryCodeAction : MongoDbCodeAction {
    override fun visitMongoDbQuery(
        coroutineScope: CoroutineScope,
        dataSource: LocalDataSource?,
        query: Node<PsiElement>,
        formatter: DialectFormatter
    ): LineMarkerInfo<PsiElement>? {
        if (dataSource == null || !dataSource.isConnected()) {
            return null
        }

        return LineMarkerInfo(
            query.sourceForMarker,
            query.sourceForMarker.textRange,
            Icons.runQueryGutter,
            { CodeActionsMessages.message("code.action.run.query") },
            { _, _ ->
                val editor = DatagripConsoleEditor.openConsoleForDataSource(query.source.project, dataSource)
                editor?.appendText(MongoshDialect.formatter.formatQuery(query, explain = false))
            },
            GutterIconRenderer.Alignment.RIGHT,
            { CodeActionsMessages.message("code.action.run.query") }
        )
    }
}