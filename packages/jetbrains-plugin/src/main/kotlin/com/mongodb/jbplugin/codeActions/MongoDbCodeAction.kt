package com.mongodb.jbplugin.codeActions

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.CoroutineScope

/**
 * This interface is for defining code actions that only depend on the parsed query. We will implement
 * this interface every time we want to add a new gutter icon that would run an action on the query.
 *
 * @see com.mongodb.jbplugin.codeActions.impl.RunQueryCodeAction
 */
interface MongoDbCodeAction {
    fun visitMongoDbQuery(
        coroutineScope: CoroutineScope,
        dataSource: LocalDataSource?,
        query: Node<PsiElement>,
        formatter: DialectFormatter,
    ): LineMarkerInfo<PsiElement>?
}
