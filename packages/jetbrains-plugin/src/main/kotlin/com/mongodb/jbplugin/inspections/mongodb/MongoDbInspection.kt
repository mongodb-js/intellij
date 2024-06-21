package com.mongodb.jbplugin.inspections.mongodb

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.mql.ast.Node

interface MongoDbInspection {
    fun visitMongoDbQuery(
        problems: ProblemsHolder,
        query: Node<PsiElement>,
    ): Unit
}
