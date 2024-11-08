package com.mongodb.jbplugin.dialects.javadriver.glossary.parser

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTypesUtil
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.parser.Parser
import com.mongodb.jbplugin.mql.parser.flatMap
import com.mongodb.jbplugin.mql.parser.mapError
import com.mongodb.jbplugin.mql.parser.requireNonNull

data object CouldNotResolveClass
fun typeToClass(): Parser<PsiType?, CouldNotResolveClass, PsiClass> {
    return requireNonNull<PsiType, _>(CouldNotResolveClass)
        .flatMap { psiType ->
            when (val resolvedClass = PsiTypesUtil.getPsiClass(psiType)) {
                null -> Either.left(CouldNotResolveClass)
                else -> Either.right(resolvedClass)
            }
        }.mapError {
            CouldNotResolveClass
        }
}

data object ClassDoesNotMatchQualifiedName
fun classIs(fqn: String): Parser<PsiClass, ClassDoesNotMatchQualifiedName, PsiClass> {
    return { input ->
        when {
            input.qualifiedName == fqn -> Either.right(input)
            else -> Either.left(ClassDoesNotMatchQualifiedName)
        }
    }
}

data object NotInAClass
fun containingClass(): Parser<PsiMethod, NotInAClass, PsiClass> {
    return { input ->
        when {
            input.containingClass != null -> Either.right(input.containingClass!!)
            else -> Either.left(NotInAClass)
        }
    }
}
