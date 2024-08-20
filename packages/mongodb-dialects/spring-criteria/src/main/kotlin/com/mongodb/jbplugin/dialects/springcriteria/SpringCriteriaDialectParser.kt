package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.mql.Node

private const val CRITERIA_CLASS_FQN = "org.springframework.data.mongodb.core.query.Criteria"

object SpringCriteriaDialectParser : DialectParser<PsiElement> {
    override fun isCandidateForQuery(source: PsiElement) = source.findCriteriaWhereExpression() != null

    override fun attachment(source: PsiElement): PsiElement = source.findCriteriaWhereExpression()!!

    override fun parse(source: PsiElement): Node<PsiElement> {
        val criteriaChain = source.findCriteriaWhereExpression() ?: return Node(source, emptyList())

        return Node(source, emptyList())
    }
}

private fun PsiElement.findCriteriaWhereExpression(): PsiExpression? {
    val methodCalls = findAllChildrenOfType(PsiMethodCallExpression::class.java)
    var bottomLevel: PsiExpression = methodCalls.find { methodCall ->
        val method = methodCall.resolveMethod() ?: return@find false
        method.containingClass?.qualifiedName == CRITERIA_CLASS_FQN &&
                method.name == "where"
    } ?: return null

    while (bottomLevel.text.startsWith("where")) {
        bottomLevel = (bottomLevel.parent as? PsiExpression) ?: return bottomLevel
    }

    return bottomLevel
}