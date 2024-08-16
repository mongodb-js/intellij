package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTypesUtil

object QueryTargetCollectionExtractor {
    fun extractCollection(expression: PsiExpression): String? {
        val templateClass = JavaPsiFacade.getInstance(expression.project).findClass(
            "org.springframework.data.mongodb.core.MongoTemplate",
            GlobalSearchScope.everythingScope(expression.project)
        ) ?: return null

        val queryMethodCall = expression.findAllChildrenOfType(PsiMethodCallExpression::class.java)
            .find {
                val methodRef = it.methodExpression.resolve() as? PsiMethod
                methodRef?.name == "query" && methodRef.containingClass == templateClass
            }

        if (queryMethodCall != null && queryMethodCall.argumentList.expressionCount > 0) {
            val queryArg = queryMethodCall.argumentList.expressions[0]
                val resolvedType = queryArg as? PsiClassObjectAccessExpression ?: return null
                val resolvedClass = PsiTypesUtil.getPsiClass(resolvedType.operand.type) ?: return null
                return ModelCollectionExtractor.fromPsiClass(resolvedClass)
        }

        return null
    }
}

private fun <T> PsiExpression.findAllChildrenOfType(type: Class<T>): List<T> {
    val allChildren = this.children.flatMap { (it as? PsiExpression)?.findAllChildrenOfType(type) ?: emptyList() }

    if (type.isInstance(this)) {
        return allChildren + listOf(this as T)
    }

    return allChildren
}