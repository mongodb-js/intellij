package com.mongodb.jbplugin.dialects.springdata.queryannotation

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.mql.ast.*
import com.mongodb.jbplugin.mql.schema.*

object SpringDataQueryAnnotationDialectParser : DialectParser<PsiElement, Dialect<PsiElement>> {
    override fun canParse(source: PsiElement): Boolean {
        return source.getSpringDataQueryMethod() != null
    }

    override fun attachment(source: PsiElement): PsiElement {
        return source.getSpringDataQueryMethod()!!
    }

    override fun parse(source: PsiElement): Node<PsiElement> {
        val queryMethod = source.getSpringDataQueryMethod()!!
        val queryAnnotation =
            queryMethod.annotations.find { it.hasQualifiedName("org.springframework.data.mongodb.repository.Query") }!!
        val valueAttr =
            queryAnnotation.findAttributeValue("value") as? PsiLiteralExpression
                ?: return Node(source, emptyArray())

        // query expression is not a valid json, so we need to "fix it"
        val queryExpression =
            valueAttr.text.trim('"')
                .replace(Regex("""\?\d+""")) {
                    "\"${it.value}\""
                }.replace('\'', '\"')

        val json = Gson().fromJson(queryExpression, JsonElement::class.java)
        return parseJson(queryMethod, json)
    }

    private fun parseJson(
        method: PsiMethod,
        json: JsonElement,
    ): Node<PsiElement> {
        when {
            json.isJsonObject -> {
                val jsonObject = json.asJsonObject!!
                if (jsonObject.size() == 1) {
                    val entry = jsonObject.entrySet().iterator().next()
                    if (!entry.key.startsWith("$")) {
                        val parameter = inferMethodParameter(method, entry.value.asString)
                        return Node(
                            parameter ?: method,
                            arrayOf(
                                Named("eq"),
                                HasFieldReference(HasFieldReference.Known(entry.key)),
                                HasValueReference(
                                    HasValueReference.Runtime(
                                        inferBsonTypeFromExpression(
                                            method,
                                            entry.value.asString,
                                        ),
                                    ),
                                ),
                                UsesRegularIndex(UsesRegularIndex.Lookup),
                            ),
                        )
                    }
                }
            }
        }

        return Node(method, emptyArray())
    }

    private fun inferMethodParameter(
        method: PsiMethod,
        expression: String,
    ): PsiParameter? {
        if (expression.startsWith("?")) {
            val argIndex = expression.replace(Regex("""[^0-9]+"""), "").toInt()
            return method.parameterList.getParameter(argIndex)
        }

        return null
    }

    private fun inferBsonTypeFromExpression(
        method: PsiMethod,
        expression: String,
    ): BsonType {
        val parameter = inferMethodParameter(method, expression) ?: return BsonNull
        return inferTypeFromParameter(parameter)
    }

    private fun inferTypeFromParameter(psiParameter: PsiParameter): BsonType {
        return when (psiParameter.type) {
            is PsiPrimitiveType ->
                when ((psiParameter.type as PsiPrimitiveType).boxedTypeName) {
                    "java.lang.Short", "java.lang.Integer" -> BsonInt32
                    "java.lang.Double", "java.lang.Float" -> BsonDouble
                    "java.lang.Boolean" -> BsonBool
                    "java.lang.Long" -> BsonInt32
                    else -> BsonNull
                }

            PsiType.getTypeByName(
                "java.lang.String",
                psiParameter.project,
                GlobalSearchScope.everythingScope(psiParameter.project),
            ),
            -> BsonString

            PsiType.getTypeByName(
                "java.util.Date",
                psiParameter.project,
                GlobalSearchScope.everythingScope(psiParameter.project),
            ),
            -> BsonDate
            null -> BsonNull
            else -> BsonNull
        }
    }
}

fun PsiElement.getSpringDataQueryMethod(): PsiMethod? {
    if (this is PsiMethod) {
        val annotations = this.annotations
        if (annotations.any { it.hasQualifiedName("org.springframework.data.mongodb.repository.Query") }) {
            return this
        }
    }

    return null
}
