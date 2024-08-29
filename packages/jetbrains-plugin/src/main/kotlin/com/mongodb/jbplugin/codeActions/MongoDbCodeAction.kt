package com.mongodb.jbplugin.codeActions

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.mql.Node

interface MongoDbCodeAction {
    fun visitMongoDbQuery(
        dataSource: LocalDataSource?,
        query: Node<PsiElement>,
        formatter: DialectFormatter,
    ): LineMarkerInfo<PsiElement>?
}
