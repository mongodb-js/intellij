package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.mongodb.jbplugin.dialects.javadriver.glossary.fuzzyResolveMethod
import com.mongodb.jbplugin.mql.components.HasCollectionReference

object QueryTargetCollectionExtractor {
    val unknown = HasCollectionReference(
        HasCollectionReference.Unknown as HasCollectionReference.CollectionReference<PsiElement>
    )

    fun extractCollectionFromQueryChain(queryChain: PsiMethodCallExpression?): HasCollectionReference<PsiElement> {
        if (queryChain == null) {
            return unknown
        }

        var currentMethodCall = queryChain
        do {
            val currentMethod = currentMethodCall?.fuzzyResolveMethod()
            if (currentMethod == null) {
                return unknown
            }

            if (currentMethod.name == "query") {
                return extractCollectionFromParameter(
                    currentMethodCall?.argumentList?.expressions?.getOrNull(0)
                )
            }

            currentMethodCall =
                currentMethodCall?.firstChild?.firstChild as? PsiMethodCallExpression
        } while (true)

        return unknown
    }

    fun extractCollectionFromParameter(sourceExpression: PsiExpression?): HasCollectionReference<PsiElement> {
        if (sourceExpression == null) {
            return unknown
        }

        val resolvedType = sourceExpression as? PsiClassObjectAccessExpression ?: return unknown
        val resolvedClass =
            PsiTypesUtil.getPsiClass(resolvedType.operand.type) ?: return unknown
        val resolvedCollection = ModelCollectionExtractor.fromPsiClass(resolvedClass)
        return resolvedCollection?.let {
            HasCollectionReference(HasCollectionReference.OnlyCollection(sourceExpression, it))
        } ?: unknown
    }

    fun HasCollectionReference<PsiElement>.or(other: HasCollectionReference<PsiElement>): HasCollectionReference<PsiElement> {
        return if (this == unknown) {
            other
        } else {
            this
        }
    }
}
