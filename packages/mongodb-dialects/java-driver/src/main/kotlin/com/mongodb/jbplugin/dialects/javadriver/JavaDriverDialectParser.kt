package com.mongodb.jbplugin.dialects.javadriver

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.mql.ast.*
import com.mongodb.jbplugin.mql.schema.BsonInt32

object JavaDriverDialectParser : DialectParser<PsiElement, Dialect<PsiElement>> {
    override suspend fun canParse(source: PsiElement): Boolean {
        val collection = source.getMongoDBCollectionElement()
        return collection != null
    }

    override suspend fun parse(source: PsiElement): Node<PsiElement> {
        val collection = source.getMongoDBCollectionElement()
        val argExpression = source.getMongoDBFilterArgument()

        val filters = parseFilters(argExpression)
        return Node(
            source,
            arrayOf(HasChildren(filters)),
        )
    }

    private fun parseFilters(source: PsiExpression?): Array<Node<PsiElement>> {
        if (source == null) {
            return emptyArray()
        }

        when (source) {
            is PsiMethodCallExpression -> {
                val qualifier = source.methodExpression.qualifierExpression ?: return emptyArray()
                val methodCall = source.methodExpression.referenceName

                if (qualifier is PsiReferenceExpression) {
                    if (qualifier.referenceName == "Filters") {
                        if (source.argumentList.expressionCount > 1) {
                            val fieldName = source.argumentList.expressions[0].text.trim('"')
                            val valueRef =
                                when (val fieldValue = source.argumentList.expressions[1]) {
                                    is PsiLiteralExpression -> {
                                        if (fieldValue.value != null) {
                                            HasValueReference.Constant(fieldValue.value!!, BsonInt32)
                                        } else {
                                            HasValueReference.Runtime(BsonInt32)
                                        }
                                    }
                                    else -> HasValueReference.Unknown
                                }

                            return arrayOf(
                                Node(
                                    source,
                                    arrayOf(
                                        Named(methodCall!!),
                                        HasFieldReference(HasFieldReference.Known(fieldName)),
                                        HasValueReference(valueRef),
                                        UsesRegularIndex(UsesRegularIndex.Lookup),
                                    ),
                                ),
                            )
                        }
                        return emptyArray()
                    }
                }
            }
        }

        return emptyArray()
    }
}

fun PsiElement.getMongoDBCollectionElement(): PsiElement? {
    if (this is PsiMethodCallExpression) {
        val qualifier = this.methodExpression.qualifierExpression ?: return null
        if (qualifier.type ==
            PsiType.getTypeByName(
                "com.mongodb.client.MongoCollection",
                this.project,
                GlobalSearchScope.everythingScope(this.project),
            )
        ) {
            return qualifier
        } else {
            return qualifier.getMongoDBCollectionElement()
        }
    } else {
        return PsiTreeUtil.getParentOfType(this, PsiMethodCallExpression::class.java)?.getMongoDBCollectionElement()
    }
}

fun PsiElement.getMongoDBFilterArgument(): PsiExpression? {
    if (this is PsiMethodCallExpression) {
        if (this.argumentList.expressionCount > 0) {
            return this.argumentList.expressions[0]
        }

        return null
    } else {
        return PsiTreeUtil.getParentOfType(this, PsiMethodCallExpression::class.java)?.getMongoDBFilterArgument()
    }
}
