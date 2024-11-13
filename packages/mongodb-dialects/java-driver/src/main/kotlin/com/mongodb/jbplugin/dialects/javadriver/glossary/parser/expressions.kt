package com.mongodb.jbplugin.dialects.javadriver.glossary.parser

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.GlobalSearchScope
import com.mongodb.jbplugin.dialects.javadriver.glossary.toBsonType
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstant
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstantString
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.flattenAnyOfReferences
import com.mongodb.jbplugin.mql.parser.Parser
import com.mongodb.jbplugin.mql.parser.map
import com.mongodb.jbplugin.mql.parser.requireNonNull
import com.mongodb.jbplugin.mql.toBsonType
import kotlin.collections.map

fun meaningfulExpression(): Parser<PsiElement, Any, PsiElement> {
    return { input ->
        fun gather(input: PsiElement): PsiElement {
            return when (input) {
                is PsiParenthesizedExpression -> if (input.children.size == 3) {
                    gather(input.children[1])
                } else {
                    input
                }
                is PsiVariable -> if (input.hasInitializer()) {
                    gather(input.initializer!!)
                } else {
                    input
                }
                is PsiReferenceExpression -> {
                    val resolution = input.resolve()
                    if (resolution != null) {
                        gather(resolution)
                    } else {
                        input
                    }
                }
                else -> input
            }
        }

        Either.right(gather(input))
    }
}

fun variable() = requireNonNull<PsiElement, PsiVariable>()
fun referenceExpression() = requireNonNull<PsiElement, PsiReferenceExpression>()

fun toFieldReference(): Parser<PsiElement, Any, HasFieldReference<PsiElement>> {
    return meaningfulExpression().map { input ->
        val fieldNameAsString = input.tryToResolveAsConstantString()
        val fieldReference = if (fieldNameAsString != null) {
            HasFieldReference.Known<PsiElement>(input, fieldNameAsString)
        } else {
            HasFieldReference.Unknown as HasFieldReference.FieldReference<PsiElement>
        }

        HasFieldReference(fieldReference)
    }
}

fun toValueReference(isArrayElement: Boolean = false): Parser<PsiElement, Any, HasValueReference<PsiElement>> {
    return { input ->
        val specifiedType = when (input) {
            is PsiVariable -> input.type
            is PsiExpression -> input.type
            else -> null
        }

        val (constant, value) = input.tryToResolveAsConstant()

        Either.right(
            HasValueReference(
                when {
                    specifiedType is PsiArrayType -> HasValueReference.Runtime(
                        input,
                        specifiedType.toBsonType()
                    )

                    specifiedType?.isJavaIterable() == true && isArrayElement -> HasValueReference.Runtime(
                        input,
                        BsonArray(specifiedType.guessIterableContentType(input.project))
                    )

                    specifiedType?.isJavaIterable() == true && !isArrayElement -> HasValueReference.Runtime(
                        input,
                        specifiedType.guessIterableContentType(input.project)
                    )

                    constant && isArrayElement -> HasValueReference.Constant(
                        input,
                        listOf(value),
                        BsonArray(value?.javaClass.toBsonType(value))
                    )

                    constant && !isArrayElement -> HasValueReference.Constant(
                        input,
                        value,
                        value?.javaClass.toBsonType(value)
                    )

                    !constant && isArrayElement -> HasValueReference.Runtime(
                        input,
                        BsonArray(
                            specifiedType?.toBsonType() ?: BsonAny
                        )
                    )

                    !constant && !isArrayElement -> HasValueReference.Runtime(
                        input,
                        specifiedType?.toBsonType() ?: BsonAny
                    )

                    else -> HasValueReference.Unknown as HasValueReference.ValueReference<PsiElement>
                }
            )
        )
    }
}

fun toValueFromArgumentList(start: Int): Parser<PsiMethodCallExpression, Any, HasValueReference<PsiElement>> {
    return { input ->
        val allConstants: List<Pair<Boolean, Any?>> = input.argumentList.expressions.slice(
            start..<input.argumentList.expressionCount
        ).map { it.tryToResolveAsConstant() }

        if (allConstants.isEmpty()) {
            Either.right(HasValueReference(HasValueReference.Runtime(input, BsonArray(BsonAny))))
        } else if (allConstants.all { it.first }) {
            val eachType = allConstants.mapNotNull {
                it.second?.javaClass?.toBsonType(it.second)
            }.map {
                flattenAnyOfReferences(it)
            }.toSet()

            if (eachType.size == 1) {
                val type = eachType.first()
                Either.right(
                    HasValueReference(
                        HasValueReference.Constant(
                            input,
                            allConstants.map { it.second },
                            BsonArray(type)
                        )
                    )
                )
            } else {
                val eachType = allConstants.mapNotNull {
                    it.second?.javaClass?.toBsonType(it.second)
                }.toSet()
                val schema = flattenAnyOfReferences(BsonAnyOf(eachType))
                Either.right(
                    HasValueReference(
                        HasValueReference.Constant(
                            input,
                            allConstants.map { it.second },
                            BsonArray(schema)
                        )
                    )
                )
            }
        } else {
            Either.right(HasValueReference(HasValueReference.Runtime(input, BsonArray(BsonAny))))
        }
    }
}

fun PsiType.guessIterableContentType(project: Project): BsonType {
    val text = canonicalText
    val start = text.indexOf('<')
    if (start == -1) {
        return BsonAny
    }
    val end = text.indexOf('>', startIndex = start)
    if (end == -1) {
        return BsonAny
    }

    val typeStr = text.substring(start + 1, end)
    return PsiType.getTypeByName(
        typeStr,
        project,
        GlobalSearchScope.everythingScope(project)
    ).toBsonType()
}
