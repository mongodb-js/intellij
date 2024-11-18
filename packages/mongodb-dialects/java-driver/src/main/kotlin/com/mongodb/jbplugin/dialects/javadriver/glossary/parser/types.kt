package com.mongodb.jbplugin.dialects.javadriver.glossary.parser

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTypesUtil
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.parser.Parser
import com.mongodb.jbplugin.mql.parser.flatMap
import com.mongodb.jbplugin.mql.parser.inputAs
import com.mongodb.jbplugin.mql.parser.mapError
import com.mongodb.jbplugin.mql.parser.matches
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

fun resolveType(): Parser<PsiElement, CouldNotResolveClass, PsiType> {
    return { input ->
        val type = if (input is PsiExpression) {
            input.type
        } else if (input is PsiVariable) {
            input.type
        } else {
            null
        }

        if (type == null) {
            Either.left(CouldNotResolveClass)
        } else {
            Either.right(type)
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

data object NotFromMongoDbCollection
fun isMethodFromDriverMongoDbCollection(): Parser<PsiMethod?, NotFromMongoDbCollection, PsiMethod> {
    return method()
        .matches(containingClass().flatMap(classIs("com.mongodb.client.MongoCollection")).matches())
        .mapError { NotFromMongoDbCollection }
        .inputAs<PsiMethod?, _, _, _>()
}

fun PsiType.isArray(): Boolean {
    return this is PsiArrayType
}

fun PsiType.isJavaIterable(): Boolean {
    if (this !is PsiClassType) {
        return false
    }

    fun recursivelyCheckIsIterable(superType: PsiClassType): Boolean {
        return superType.canonicalText.startsWith("java.lang.Iterable") ||
            superType.superTypes.any {
                it.canonicalText.startsWith("java.lang.Iterable") ||
                    if (it is PsiClassType) {
                        recursivelyCheckIsIterable(it)
                    } else {
                        false
                    }
            }
    }

    return recursivelyCheckIsIterable(this)
}