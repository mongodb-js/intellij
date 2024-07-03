package com.mongodb.jbplugin.dialects.javadriver.glossary.abstractions

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.dialects.javadriver.glossary.Abstraction
import com.mongodb.jbplugin.dialects.javadriver.glossary.findContainingClass
import com.mongodb.jbplugin.dialects.javadriver.glossary.isMongoDbClass

object DriverInFactoryMethodAbstraction : Abstraction {
    override fun isIn(psiElement: PsiElement): Boolean {
        val containingClass = psiElement.findContainingClass()

        if (!AbstractRepositoryDaoAbstraction.isIn(containingClass) && !RepositoryDaoAbstraction.isIn(containingClass)
        ) {
            return false
        }

        val allMethodCallExpressions = PsiTreeUtil.findChildrenOfType(psiElement, PsiMethodCallExpression::class.java)

        val callsReturningMongoDbObjects =
            allMethodCallExpressions.filter {
                it.methodExpression.qualifierExpression
                    ?.type
                    ?.isMongoDbClass(psiElement.project) == true
            }

        val methodsOfPreviousCalls =
            callsReturningMongoDbObjects
                .mapNotNull {
                    it.methodExpression.reference?.resolve()
                }.filterIsInstance<PsiMethod>()

        return methodsOfPreviousCalls.any {
            val methodCalls =
                PsiTreeUtil
                    .findChildrenOfType(psiElement, PsiMethodCallExpression::class.java)

            methodCalls.any {
                it.methodExpression.qualifierExpression
                    ?.type
                    ?.isMongoDbClass(it.project) == true &&
                    it.methodExpression.qualifiedName.contains("getDatabase") ||
                    it.methodExpression.qualifiedName.contains("getCollection")
            }
        }
    }
}
