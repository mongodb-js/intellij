package com.mongodb.jbplugin.dialects.javadriver.glossary.parser

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentsOfType
import com.mongodb.jbplugin.dialects.javadriver.glossary.collectTypeUntil
import com.mongodb.jbplugin.dialects.javadriver.glossary.findAllChildrenOfType
import com.mongodb.jbplugin.dialects.javadriver.glossary.fuzzyResolveMethod
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.parser.Parser
import com.mongodb.jbplugin.mql.parser.acceptAnyError
import com.mongodb.jbplugin.mql.parser.flatMap
import com.mongodb.jbplugin.mql.parser.mapAs
import com.mongodb.jbplugin.mql.parser.mapError
import com.mongodb.jbplugin.mql.parser.mapMany
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

fun method() = requireNonNull<PsiElement, PsiMethod>()
fun methodCall() = requireNonNull<PsiElement, PsiMethodCallExpression>()

data object ArgumentNotFound

fun allArguments(): Parser<PsiMethodCallExpression, Any, List<PsiExpression>> {
    val baseParser: Parser<PsiMethodCallExpression, Any, List<PsiExpression>> = { input ->
        Either.right(input.argumentList.expressions.toList())
    }

    return baseParser
        .mapMany(meaningfulExpression())
        .mapAs<List<PsiExpression>, _, _, _>()
        .acceptAnyError()
}

fun argumentAt(n: Int): Parser<PsiMethodCallExpression, ArgumentNotFound, PsiElement> {
    val baseParser: Parser<PsiMethodCallExpression, ArgumentNotFound, PsiExpression> = { input ->
        val arg = input.argumentList.expressions.getOrNull(n)
        if (arg != null) {
            Either.right(arg)
        } else {
            Either.left(ArgumentNotFound)
        }
    }

    return baseParser
        .flatMap(meaningfulExpression())
        .mapError { ArgumentNotFound }
}

fun allChildrenMethodCalls(): Parser<PsiElement, Any, List<PsiMethodCallExpression>> {
    return { input ->
        Either.right(
            input.collectTypeUntil(
                PsiMethodCallExpression::class.java,
                PsiReturnStatement::class.java
            )
        )
    }
}

fun methodCallChain(): Parser<PsiMethodCallExpression, Any, List<PsiMethodCallExpression>> {
    return { input ->
        // by reversing the list, we get the "deepest" (closest) levels first
        val allCallExpressions = input.findAllChildrenOfType(
            PsiMethodCallExpression::class.java
        ).reversed() + input.parentsOfType<PsiMethodCallExpression>()

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
