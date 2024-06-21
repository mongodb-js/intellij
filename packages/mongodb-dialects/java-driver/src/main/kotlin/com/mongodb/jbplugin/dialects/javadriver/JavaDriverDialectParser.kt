package com.mongodb.jbplugin.dialects.javadriver

import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.mql.ast.*
import com.mongodb.jbplugin.mql.schema.*

object JavaDriverDialectParser : DialectParser<PsiElement, Dialect<PsiElement>> {
    override fun canParse(source: PsiElement): Boolean {
        val collection = source.getMongoDBCollectionElement()
        return collection != null
    }

    override fun attachment(source: PsiElement): PsiElement {
        val collection = source.getMongoDBCollectionElement() ?: throw NoSuchElementException()
        val attachment = PsiTreeUtil.getParentOfType(collection, PsiMethodCallExpression::class.java)!!

        return attachment
    }

    override fun parse(source: PsiElement): Node<PsiElement> {
        val collection = source.getMongoDBCollectionElement() ?: return Node(source, emptyArray())
        val argExpression = collection.getMongoDBFilterArgument()

        val filters = parseFilters(argExpression)
        return Node(
            PsiTreeUtil.getParentOfType(collection, PsiMethodCallExpression::class.java)!!,
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
                                            HasValueReference.Constant(fieldValue.value!!, inferTypeFromExpression(fieldValue))
                                        } else {
                                            HasValueReference.Runtime(inferTypeFromExpression(fieldValue))
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

    private fun inferTypeFromExpression(psiExpression: PsiExpression): BsonType {
        return when (psiExpression.type) {
            is PsiPrimitiveType ->
                when ((psiExpression.type as PsiPrimitiveType).boxedTypeName) {
                    "java.lang.Short", "java.lang.Integer" -> BsonInt32
                    "java.lang.Double", "java.lang.Float" -> BsonDouble
                    "java.lang.Boolean" -> BsonBool
                    "java.lang.Long" -> BsonInt32
                    else -> BsonNull
                }
            PsiType.getTypeByName(
                "java.lang.String",
                psiExpression.project,
                GlobalSearchScope.everythingScope(psiExpression.project),
            ),
            -> BsonString
            PsiType.getTypeByName(
                "java.util.Date",
                psiExpression.project,
                GlobalSearchScope.everythingScope(psiExpression.project),
            ),
            -> BsonDate
            null -> BsonNull
            else -> BsonNull
        }
    }
}

fun PsiElement.getMongoDBCollectionElement(): PsiElement? {
    if (this is PsiMethodCallExpression) {
        val qualifier = this.methodExpression.qualifierExpression ?: return null
        if (qualifier.type?.equalsToText("com.mongodb.client.MongoCollection<org.bson.Document>") == true) {
            return qualifier
        } else {
            return qualifier.parent.getMongoDBCollectionElement()
        }
    }
    return null
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
