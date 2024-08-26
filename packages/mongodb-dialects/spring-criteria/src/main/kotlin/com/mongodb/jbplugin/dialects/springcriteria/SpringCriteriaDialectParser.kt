package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.dialects.javadriver.glossary.toBsonType
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstant
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstantString
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.*
import com.mongodb.jbplugin.mql.toBsonType

private const val CRITERIA_CLASS_FQN = "org.springframework.data.mongodb.core.query.Criteria"

object SpringCriteriaDialectParser : DialectParser<PsiElement> {
    override fun isCandidateForQuery(source: PsiElement) = source.findCriteriaWhereExpression() != null

    override fun attachment(source: PsiElement): PsiElement = source.findCriteriaWhereExpression()!!

    override fun parse(source: PsiElement): Node<PsiElement> {
        if (source !is PsiExpression) {
            return Node(source, emptyList())
        }

        val criteriaChain = source.findCriteriaWhereExpression() ?: return Node(source, emptyList())
        val collectionReference = HasCollectionReference(
            QueryTargetCollectionExtractor.extractCollection(criteriaChain)?.let {
                HasCollectionReference.OnlyCollection(it)
            } ?: HasCollectionReference.Unknown
        )
        return Node(
            source,
            listOf(
                collectionReference,
                HasChildren(parseQueryRecursively(criteriaChain))
            )
        )
    }

    private fun parseQueryRecursively(fieldNameCall: PsiMethodCallExpression): List<Node<PsiElement>> {
        if (!fieldNameCall.isCriteriaQueryMethod()) {
            return emptyList()
        }

        val valueCall = fieldNameCall.parent.parent as? PsiMethodCallExpression ?: return emptyList()

        val fieldName = fieldNameCall.argumentList.expressions[0].tryToResolveAsConstantString()!!
        val (isResolved, value) = valueCall.argumentList.expressions[0].tryToResolveAsConstant()
        val name = valueCall.resolveMethod()?.name!!

        val fieldReference = HasFieldReference(
            HasFieldReference.Known(fieldNameCall.argumentList.expressions[0], fieldName)
        )

        val valueReference = HasValueReference(
            if (isResolved) {
                HasValueReference.Constant(valueCall, value, value!!.javaClass.toBsonType(value))
            } else {
                HasValueReference.Runtime(
                    valueCall,
                    valueCall.argumentList.expressions[0].type?.toBsonType() ?: BsonAny
                )
            }
        )

        val predicate = Node<PsiElement>(
            fieldNameCall, listOf(
                Named(name),
                fieldReference,
                valueReference
            )
        )

        if (valueCall.parent.parent is PsiMethodCallExpression) {
            val nextField = valueCall.parent.parent as PsiMethodCallExpression
            return listOf(predicate) + parseQueryRecursively(nextField)
        }

        return listOf(predicate)
    }
}

private fun PsiElement.findCriteriaWhereExpression(): PsiMethodCallExpression? {
    val methodCalls = findAllChildrenOfType(PsiMethodCallExpression::class.java)
    var bottomLevel: PsiMethodCallExpression = methodCalls.find { methodCall ->
        val method = methodCall.resolveMethod() ?: return@find false
        method.containingClass?.qualifiedName == CRITERIA_CLASS_FQN &&
                method.name == "where"
    } ?: return null

    while (bottomLevel.text.startsWith("where")) {
        bottomLevel = (bottomLevel.parent as? PsiMethodCallExpression) ?: return bottomLevel
    }

    return bottomLevel
}

private fun PsiMethodCallExpression.isCriteriaQueryMethod(): Boolean {
    val method = resolveMethod() ?: return false
    return method.containingClass?.qualifiedName == CRITERIA_CLASS_FQN
}