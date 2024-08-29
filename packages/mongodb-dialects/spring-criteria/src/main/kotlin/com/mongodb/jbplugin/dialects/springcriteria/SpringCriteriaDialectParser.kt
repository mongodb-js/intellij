package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.dialects.javadriver.glossary.findAllChildrenOfType
import com.mongodb.jbplugin.dialects.javadriver.glossary.toBsonType
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstant
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstantString
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.*
import com.mongodb.jbplugin.mql.toBsonType

private const val CRITERIA_CLASS_FQN = "org.springframework.data.mongodb.core.query.Criteria"
private const val DOCUMENT_FQN = "org.springframework.data.mongodb.core.mapping.Document"

object SpringCriteriaDialectParser : DialectParser<PsiElement> {
    override fun isCandidateForQuery(source: PsiElement) = source.findCriteriaWhereExpression() != null

    override fun attachment(source: PsiElement): PsiElement = source.findCriteriaWhereExpression()!!

    override fun parse(source: PsiElement): Node<PsiElement> {
        if (source !is PsiExpression) {
            return Node(source, emptyList())
        }

        val criteriaChain = source.findCriteriaWhereExpression() ?: return Node(source, emptyList())
        val targetCollection = QueryTargetCollectionExtractor.extractCollection(source)

        return Node(source, listOf(
            HasCollectionReference(targetCollection?.let {
                HasCollectionReference.OnlyCollection(targetCollection)
            } ?: HasCollectionReference.Unknown),
            HasChildren(parseQueryRecursively(criteriaChain))
        ))
    }

    override fun isReferenceToDatabase(source: PsiElement): Boolean {
        return false // databases are in property files and we don't support AC there yet
    }

    override fun isReferenceToCollection(source: PsiElement): Boolean {
        val docAnnotation = source.parentOfType<PsiAnnotation>() ?: return false
        return docAnnotation.hasQualifiedName(DOCUMENT_FQN)
    }

    override fun isReferenceToField(source: PsiElement): Boolean {
        val isString = source.parentOfType<PsiLiteralExpression>(true)?.tryToResolveAsConstantString() != null
        val methodCall = source.parentOfType<PsiMethodCallExpression>() ?: return false

        return isString && methodCall.isCriteriaExpression()
    }

    private fun parseQueryRecursively(
        fieldNameCall: PsiMethodCallExpression,
        until: PsiElement? = null
    ): List<Node<PsiElement>> {
        val valueCall = fieldNameCall.parentMethodCallExpression() ?: return emptyList()

        if (!fieldNameCall.isCriteriaQueryMethod() || fieldNameCall == until || valueCall == until) {
            return emptyList()
        }

        val currentCriteriaMethod = fieldNameCall.resolveMethod() ?: return emptyList()
        if (currentCriteriaMethod.isVarArgs) {
            val allSubQueries = fieldNameCall.argumentList.expressions
                .filterIsInstance<PsiMethodCallExpression>()
                .map { it.innerMethodCallExpression() }
                .flatMap { parseQueryRecursively(it, fieldNameCall) }

            if (fieldNameCall.parent.parent is PsiMethodCallExpression) {
                val named = operatorName(currentCriteriaMethod)
                val nextField = fieldNameCall.parent.parent as PsiMethodCallExpression
                return listOf(Node<PsiElement>(fieldNameCall, listOf(named, HasChildren(allSubQueries)))) +
                        parseQueryRecursively(nextField, until)
            }
        }

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

        valueCall.parentMethodCallExpression()?.let {
            return listOf(predicate) + parseQueryRecursively(it, until)
        }

        return listOf(predicate)
    }

    private fun operatorName(currentCriteriaMethod: PsiMethod): Named {
        val name = currentCriteriaMethod.name.replace("Operator", "")
        val named = Named(name)
        return named
    }
}

/**
 * Returns whether the current method is a criteria method.
 *
 * @return
 */
fun PsiMethodCallExpression.isCriteriaExpression(): Boolean {
    val method = resolveMethod() ?: return false
    return method.containingClass?.qualifiedName == CRITERIA_CLASS_FQN
}

private fun PsiElement.findCriteriaWhereExpression(): PsiMethodCallExpression? {
    val parentStatement = PsiTreeUtil.getParentOfType(this, PsiStatement::class.java) ?: return null
    val methodCalls = parentStatement.findAllChildrenOfType(PsiMethodCallExpression::class.java)
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

private fun PsiMethodCallExpression.parentMethodCallExpression(): PsiMethodCallExpression? {
    // In this function, we have an expression similar to:
    // a().b()
    // ^  ^ ^ this is B, the current method call expression
    // |  | this is one parent (a reference to the current method)
    // | this is parent.parent (the previous call expression)
    return parent.parent as? PsiMethodCallExpression
}

private fun PsiMethodCallExpression.innerMethodCallExpression(): PsiMethodCallExpression {
    // Navigates downwards until the end of the query chain:
    // a().b()
    // ^   ^ ^
    // |   | this is children[0].children[0]
    // |   | this is children[0]
    // | this is the current method call expression
    // we do it recursively because there is an indeterminate amount of chains
    var ref = this
    while (isCriteriaQueryMethod()) {
        val next = ref.children[0].children[0] as? PsiMethodCallExpression ?: return ref
        ref = next
    }

    return ref
}