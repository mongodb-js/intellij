package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.parentOfType

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

/**
 * Returns all children of type in a list. Order is not guaranteed between calls.
 * It also takes into consideration in method calls, the parameters of the method call.
 *
 * @param type
 */
fun <T> PsiElement.findAllChildrenOfType(type: Class<T>): List<T> {
    var allChildren = this.children.flatMap { it.findAllChildrenOfType(type) }

    if (this is PsiMethodCallExpression) {
        allChildren += this.argumentList.expressions.flatMap { it.findAllChildrenOfType(type) }
    }

    if (type.isInstance(this)) {
        allChildren += listOf(this as T)
    }

    return allChildren
}