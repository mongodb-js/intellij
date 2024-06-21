package com.mongodb.jbplugin.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.dialects.javadriver.JavaDriverDialect
import com.mongodb.jbplugin.i18n.InspectionsMessages
import com.mongodb.jbplugin.linting.rules.NotCompatibleTypes
import com.mongodb.jbplugin.linting.rules.TypeCheckQuery
import com.mongodb.jbplugin.mql.ast.Node
import com.mongodb.jbplugin.mql.schema.BsonDocument
import com.mongodb.jbplugin.mql.schema.BsonInt32
import com.mongodb.jbplugin.mql.schema.Collection
import kotlinx.coroutines.runBlocking

class JavaDriverTypeCheckInspection : AbstractJavaMongoDbInspection(JavaDriverDialect) {
    override fun visitMongoDbQuery(
        problems: ProblemsHolder,
        query: Node<PsiElement>,
    ) {
        val warnings =
            runBlocking {
                TypeCheckQuery.apply(
                    query,
                    Collection(
                        BsonDocument(mapOf("myField" to BsonInt32)),
                    ),
                )
            }

        for (it in warnings) {
            val message =
                when (val warning = it.warning) {
                    is NotCompatibleTypes ->
                        InspectionsMessages.message(
                            "inspection.not.compatible.type.checks",
                            warning.codeType.toString(),
                            warning.schemaType.toString(),
                        )
                }

            problems.registerProblem(
                it.node.source,
                message,
                ProblemHighlightType.WARNING,
            )
        }
    }
}
