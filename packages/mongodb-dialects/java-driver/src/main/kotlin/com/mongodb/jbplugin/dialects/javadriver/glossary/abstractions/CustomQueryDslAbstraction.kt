package com.mongodb.jbplugin.dialects.javadriver.glossary.abstractions

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.dialects.javadriver.glossary.Abstraction
import com.mongodb.jbplugin.dialects.javadriver.glossary.findContainingClass
import com.mongodb.jbplugin.dialects.javadriver.glossary.isUsingMongoDbClasses

object CustomQueryDslAbstraction : Abstraction {
    override fun isIn(psiElement: PsiElement): Boolean {
        if (!AbstractRepositoryDaoAbstraction.isIn(psiElement)) {
            return false
        }

        val containingClass = psiElement.findContainingClass()
        val parentRepositoryClass = containingClass.superClass!!

        val queryLikeMethods =
            containingClass.allMethods.filter {
                it.name.startsWith("find")
            }

        if (queryLikeMethods.isEmpty()) {
            return false
        }

        return queryLikeMethods.any { method ->
            val allMethodCallExpressions = PsiTreeUtil.findChildrenOfType(method, PsiMethodCallExpression::class.java)
            allMethodCallExpressions.any { methodCall ->
                val methodCallName = methodCall.methodExpression.referenceName ?: return@any false
                val isReferencedInParentClass = parentRepositoryClass.allMethods.any { it.name == methodCallName }

                if (isReferencedInParentClass) {
                    isIn(parentRepositoryClass)
                } else {
                    false
                }
            } ||
                DriverInFactoryMethodAbstraction.isIn(method) ||
                method.isUsingMongoDbClasses()
        }
    }
}
