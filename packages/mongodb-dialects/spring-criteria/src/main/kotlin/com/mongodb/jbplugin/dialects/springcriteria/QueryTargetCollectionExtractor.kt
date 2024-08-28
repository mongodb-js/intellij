package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.parentOfType
import com.mongodb.jbplugin.dialects.javadriver.glossary.findAllChildrenOfType

object QueryTargetCollectionExtractor {
    fun extractCollection(sourceExpression: PsiElement): String? {
        val baseExpression = sourceExpression.parentOfType<PsiMethod>() ?: return null

        val templateClass = JavaPsiFacade.getInstance(baseExpression.project).findClass(
            "org.springframework.data.mongodb.core.MongoTemplate",
            GlobalSearchScope.everythingScope(baseExpression.project)
        ) ?: return null

        val validMethods = setOf("query", "find")
        val queryMethodCall = baseExpression.findAllChildrenOfType(PsiMethodCallExpression::class.java)
            .find {
                val methodRef = it.methodExpression.resolve() as? PsiMethod
                validMethods.contains(methodRef?.name) && methodRef?.containingClass == templateClass
            }

        if (queryMethodCall != null && queryMethodCall.argumentList.expressionCount > 0) {
            val queryArg = queryMethodCall.argumentList.expressions.last()
            val resolvedType = queryArg as? PsiClassObjectAccessExpression ?: return null
            val resolvedClass = PsiTypesUtil.getPsiClass(resolvedType.operand.type) ?: return null
            return ModelCollectionExtractor.fromPsiClass(resolvedClass)
        }

        return null
    }
}