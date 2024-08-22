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

/**
 * Returns all children of type in a list. Order is not guaranteed between calls.
 * It also takes into consideration in method calls, the parameters of the method call.
 *
 * @param type
 */
fun <T> PsiElement.findAllChildrenOfType(type: Class<T>): List<T> {
    var allChildren = this.children.flatMap { (it as? PsiExpression)?.findAllChildrenOfType(type) ?: emptyList() }

    if (this is PsiMethodCallExpression) {
        allChildren += this.argumentList.expressions.flatMap { it.findAllChildrenOfType(type) }
    }

    if (type.isInstance(this)) {
        allChildren += listOf(this as T)
    }

    return allChildren
}