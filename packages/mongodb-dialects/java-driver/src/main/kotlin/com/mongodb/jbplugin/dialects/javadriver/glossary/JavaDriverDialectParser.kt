package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.*
import com.mongodb.jbplugin.mql.toBsonType

object JavaDriverDialectParser : DialectParser<PsiElement> {
    override fun isCandidateForQuery(source: PsiElement): Boolean {
        if (source !is PsiMethodCallExpression) {
            return false
        }
        val sourceMethod = source.resolveMethod() ?: return false

        if (
            sourceMethod.containingClass?.isMongoDbCollectionClass(source.project) == true
        ) {
            return true
        }

        if (
            sourceMethod.containingClass?.isMongoDbClass(source.project) == true
        ) {
            val allChildrenCandidates = PsiTreeUtil.findChildrenOfType(source, PsiMethodCallExpression::class.java)
            return allChildrenCandidates.any { isCandidateForQuery(it) }
        }

        return false
    }

    override fun attachment(source: PsiElement): PsiElement = source.findMongoDbCollectionReference()!!

    override fun parse(source: PsiElement): Node<PsiElement> {
        val namespace = NamespaceExtractor.extractNamespace(source)
        val currentCall = source as PsiMethodCallExpression? ?: return Node(source, emptyList())

        val hasChildren =
            if (currentCall.argumentList.expressionCount > 0) {
                val argumentAsFilters = currentCall.argumentList.expressions[0] as? PsiMethodCallExpression
                argumentAsFilters?.let {
val parsedQuery = parseFilterExpression(argumentAsFilters)
parsedQuery?.let {
HasChildren(
listOf(parseFilterExpression(
currentCall.argumentList.expressions[0] as PsiMethodCallExpression
)!!),
)
} ?: HasChildren(emptyList())
} ?: HasChildren(emptyList())
            } else {
                HasChildren(emptyList())
            }

        return Node(
            source,
            listOf(
                namespace?.let {
                    HasCollectionReference(HasCollectionReference.Known(namespace))
                } ?: HasCollectionReference(HasCollectionReference.Unknown),
                hasChildren,
            ),
        )
    }

    private fun parseFilterExpression(filter: PsiMethodCallExpression): Node<PsiElement>? {
        val method = filter.resolveMethod() ?: return null
        if (method.isVarArgs) {
            return Node(
                filter,
                listOf(
                    Named(method.name),
                    HasChildren(
                        filter.argumentList.expressions
                            .filterIsInstance<PsiMethodCallExpression>()
                            .mapNotNull { parseFilterExpression(it) },
                    ),
                ),
            )
        } else if (method.parameters.size == 2) {
            val fieldNameAsString = filter.argumentList.expressions[0].tryToResolveAsConstantString()
            val fieldReference =
                fieldNameAsString?.let {
HasFieldReference.Known(filter.argumentList.expressions[0], fieldNameAsString)
} ?: HasFieldReference.Unknown

            val constantValue = filter.argumentList.expressions[1].tryToResolveAsConstant()
            val typeOfConstantValue = constantValue?.javaClass?.toBsonType()

            val valueReference =
                if (constantValue != null && typeOfConstantValue != null) {
                    HasValueReference.Constant(constantValue, typeOfConstantValue)
                } else {
                    val psiTypeOfValue =
                        filter.argumentList.expressions[1]
                            .type
                            ?.toBsonType()
                    psiTypeOfValue?.let {
HasValueReference.Runtime(psiTypeOfValue)
} ?: HasValueReference.Unknown
                }

            return Node(
                filter,
                listOf(
                    Named(method.name),
                    HasFieldReference(
                        fieldReference,
                    ),
                    HasValueReference(
                        valueReference,
                    ),
                ),
            )
        }

        return null
    }
}
