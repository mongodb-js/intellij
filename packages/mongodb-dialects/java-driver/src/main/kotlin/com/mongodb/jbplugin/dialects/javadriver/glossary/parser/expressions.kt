package com.mongodb.jbplugin.dialects.javadriver.glossary.parser

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.mongodb.jbplugin.dialects.javadriver.glossary.guessIterableContentType
import com.mongodb.jbplugin.dialects.javadriver.glossary.inferValueReferenceFromVarArg
import com.mongodb.jbplugin.dialects.javadriver.glossary.isJavaIterable
import com.mongodb.jbplugin.dialects.javadriver.glossary.toBsonType
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstant
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstantString
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasValueReference
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

fun variable() = requireNonNull<PsiVariable>()
fun referenceExpression() = requireNonNull<PsiReferenceExpression>()

fun toFieldReference(): Parser<PsiElement, Any, HasFieldReference<PsiElement>> {
    return meaningfulExpression().map { input ->
        val fieldNameAsString = input.tryToResolveAsConstantString()
        val fieldReference =
            fieldNameAsString?.let {
                HasFieldReference.Known<PsiElement>(input, it)
            } ?: (HasFieldReference.Unknown as HasFieldReference.FieldReference<PsiElement>)

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
        val result = input.argumentList.inferValueReferenceFromVarArg(start)
        Either.right(HasValueReference(result))
    }
}
