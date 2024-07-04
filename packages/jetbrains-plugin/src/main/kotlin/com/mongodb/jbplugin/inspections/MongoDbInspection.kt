package com.mongodb.jbplugin.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.mql.Node

/**
 * A MongoDb inspection that works on an MQL AST.
 * This inspection is dialect agnostic.
 */
interface MongoDbInspection {
    fun visitMongoDbQuery(
        problems: ProblemsHolder,
        query: Node<PsiElement>,
    )
}
