package com.mongodb.jbplugin.dialects.javadriver.glossary.parser

import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.dialects.javadriver.glossary.findAllChildrenOfType
import com.mongodb.jbplugin.dialects.javadriver.glossary.fuzzyResolveMethod
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.parser.Parser
import com.mongodb.jbplugin.mql.parser.requireNonNull

data object CouldNotResolveMethod
fun resolveMethod(): Parser<PsiMethodCallExpression?, CouldNotResolveMethod, PsiMethod> {
    return { input ->
        val resolvedMethodIfTypesAvailable = input?.resolveMethod()
        val fuzzyResolvedMethod = if (resolvedMethodIfTypesAvailable ==
            null
        ) {
            input?.fuzzyResolveMethod()
        } else {
            null
        }

        when {
            resolvedMethodIfTypesAvailable != null -> Either.right(resolvedMethodIfTypesAvailable)
            fuzzyResolvedMethod != null -> Either.right(fuzzyResolvedMethod)
            else -> Either.left(CouldNotResolveMethod)
        }
    }
}

fun method() = requireNonNull<PsiMethod>()
fun methodCall() = requireNonNull<PsiMethodCallExpression>()

fun methodCallChain(): Parser<PsiMethodCallExpression, Any, List<PsiMethodCallExpression>> {
    return { input ->
        // by reversing the list, we get the "deepest" (closest) levels first
        val allCallExpressions = input.findAllChildrenOfType(
            PsiMethodCallExpression::class.java
        ).reversed()
        Either.right(allCallExpressions)
    }
}

fun methodName(): Parser<PsiMethod, Any, String> {
    return { input ->
        Either.right(input.name)
    }
}

fun methodReturnStatements(): Parser<PsiMethod, Any, List<PsiExpression>> {
    return { input ->
        val allReturns = PsiTreeUtil.findChildrenOfType(
            input.body,
            PsiReturnStatement::class.java
        ).mapNotNull { it.returnValue }

        Either.right(allReturns)
    }
}
