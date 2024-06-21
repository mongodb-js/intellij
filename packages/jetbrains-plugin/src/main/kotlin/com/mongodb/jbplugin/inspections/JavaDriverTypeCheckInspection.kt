package com.mongodb.jbplugin.inspections

import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.dialects.javadriver.JavaDriverDialect
import com.mongodb.jbplugin.mql.ast.Node
import kotlinx.coroutines.CoroutineScope

class JavaDriverTypeCheckInspection(
    coroutineScope: CoroutineScope,
) : AbstractJavaMongoDbInspection(coroutineScope, JavaDriverDialect) {
    override suspend fun visitMongoDbQuery(query: Node<PsiElement>) {
        println(query)
    }
}
