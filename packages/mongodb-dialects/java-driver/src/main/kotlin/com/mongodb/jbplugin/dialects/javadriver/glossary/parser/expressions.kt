package com.mongodb.jbplugin.dialects.javadriver.glossary.parser

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.parser.Parser
import com.mongodb.jbplugin.mql.parser.flatMap
import com.mongodb.jbplugin.mql.parser.mapAs
import com.mongodb.jbplugin.mql.parser.mapError
import com.mongodb.jbplugin.mql.parser.requireNonNull

data object CouldNotResolveExpression

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

fun allReturnsExpressionsOfMethod(): Parser<PsiMethod, Any, List<PsiReturnStatement>> {
    return { input ->
        Either.right(PsiTreeUtil.findChildrenOfType(input, PsiReturnStatement::class.java).toList())
    }
}

fun returnValue(): Parser<PsiReturnStatement, CouldNotResolveExpression, PsiExpression> {
    val base: Parser<PsiReturnStatement, CouldNotResolveExpression, PsiExpression> = { input ->
        if (input.returnValue == null) {
            Either.left(CouldNotResolveExpression)
        } else {
            Either.right(input.returnValue!!)
        }
    }

    return base.flatMap(meaningfulExpression())
        .mapAs<PsiExpression, _, _, _>()
        .mapError { CouldNotResolveExpression }
}
