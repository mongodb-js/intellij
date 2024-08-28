package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.*
import com.mongodb.jbplugin.mql.toBsonType

private const val FILTERS_FQN = "com.mongodb.client.model.Filters"
private const val UPDATES_FQN = "com.mongodb.client.model.Updates"

object JavaDriverDialectParser : DialectParser<PsiElement> {
    override fun isCandidateForQuery(source: PsiElement): Boolean =
 runCatching { findStartOfQuery(source) }.getOrNull() != null

    override fun attachment(source: PsiElement): PsiElement = findStartOfQuery(source)!!

    override fun parse(source: PsiElement): Node<PsiElement> {
        val namespace = NamespaceExtractor.extractNamespace(source)
        val collectionReference = namespaceComponent(namespace)

        val currentCall = source as? PsiMethodCallExpression ?: return Node(source, listOf(collectionReference))

        val calledMethod = currentCall.resolveMethod()
        if (calledMethod?.containingClass?.isMongoDbCollectionClass(source.project) == true) {
            val hasChildren =
                HasChildren(
                    parseAllFiltersFromCurrentCall(currentCall) +
                            parseAllUpdatesFromCurrentCall(currentCall),
                )

            return Node(
                source,
                listOf(
                    collectionReference,
                    hasChildren,
                ),
            )
        } else {
            calledMethod?.let {
 // if it's another class, try to resolve the query from the method body
                val allReturns = PsiTreeUtil.findChildrenOfType(calledMethod.body, PsiReturnStatement::class.java)
                return allReturns
                    .mapNotNull { it.returnValue }
                    .flatMap {
                        it.collectTypeUntil(PsiMethodCallExpression::class.java, PsiReturnStatement::class.java)
                    }.firstNotNullOfOrNull {
                        val innerQuery = parse(it)
                        if (!innerQuery.hasComponent<HasChildren<PsiElement>>()) {
                            null
                        } else {
                            innerQuery
                        }
                    } ?: Node(source, listOf(collectionReference))
            } ?: return Node(source, listOf(collectionReference))
        }
    }

    private fun parseAllFiltersFromCurrentCall(currentCall: PsiMethodCallExpression): List<Node<PsiElement>> =
        if (currentCall.argumentList.expressionCount > 0) {
            // we have at least 1 argument in the current method call
            // try to get the relevant filter calls, or avoid parsing the query at all
            val argumentAsFilters = resolveToFiltersCall(currentCall.argumentList.expressions[0])
            argumentAsFilters?.let {
                val parsedQuery = parseFilterExpression(argumentAsFilters)
                parsedQuery?.let {
                    listOf(
                        parsedQuery,
                    )
                } ?: emptyList()
            } ?: emptyList()
        } else {
            emptyList()
        }

    private fun parseAllUpdatesFromCurrentCall(currentCall: PsiMethodCallExpression): List<Node<PsiElement>> =
        if (currentCall.argumentList.expressionCount > 1) {
            val argumentAsUpdates = resolveToUpdatesCall(currentCall.argumentList.expressions[1])
            // parse only if it's a call to `updates` methods
            argumentAsUpdates?.let {
                val parsedQuery = parseUpdatesExpression(argumentAsUpdates)
                parsedQuery?.let {
                    listOf(parsedQuery)
                } ?: emptyList()
            } ?: emptyList()
        } else {
            emptyList()
        }

    override fun isReferenceToDatabase(source: PsiElement): Boolean {
        val refToDb =
            source
                .parentOfType<PsiMethodCallExpression>(true)
                ?.findMongoDbClassReference(source.project)
                ?: return false

        return refToDb.type?.isMongoDbDatabaseClass(refToDb.project) == true
    }

    override fun isReferenceToCollection(source: PsiElement): Boolean {
        val refToDb =
            source
                .parentOfType<PsiMethodCallExpression>(true)
                ?.findMongoDbClassReference(source.project)
                ?: return false

        return refToDb.type?.isMongoDbCollectionClass(refToDb.project) == true
    }

    override fun isReferenceToField(source: PsiElement): Boolean {
        val isInQuery = isInQuery(source)
        val isString = source.parentOfType<PsiLiteralExpression>()?.tryToResolveAsConstantString() != null

        return isInQuery && isString
    }

    private fun isInQuery(element: PsiElement): Boolean {
        val methodCall = element.parentOfType<PsiMethodCallExpression>(false) ?: return false
        val containingClass = methodCall.resolveMethod()?.containingClass ?: return false

        return containingClass.qualifiedName == FILTERS_FQN || containingClass.qualifiedName == UPDATES_FQN
    }

    private fun parseFilterExpression(filter: PsiMethodCallExpression): Node<PsiElement>? {
        val method = filter.resolveMethod() ?: return null
        if (method.isVarArgs) {
// Filters.and, Filters.or... are varargs
            return Node(
                filter,
                listOf(
                    Named(method.name),
                    HasChildren(
                        filter.argumentList.expressions
                            .mapNotNull { resolveToFiltersCall(it) }
                            .mapNotNull { parseFilterExpression(it) },
                    ),
                ),
            )
        } else if (method.parameters.size == 2) {
            // If it has two parameters, it's field/value.
            val fieldReference = resolveFieldNameFromExpression(filter.argumentList.expressions[0])
            val valueReference = resolveValueFromExpression(filter.argumentList.expressions[1])

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
// here we really don't know much, so just don't attempt to parse the query
        return null
    }

    private fun resolveToFiltersCall(element: PsiElement): PsiMethodCallExpression? {
        when (element) {
            is PsiMethodCallExpression -> {
                val method = element.resolveMethod() ?: return null
                if (method.containingClass?.qualifiedName == FILTERS_FQN) {
                    return element
                }
                val allReturns = PsiTreeUtil.findChildrenOfType(method.body, PsiReturnStatement::class.java)
                return allReturns.mapNotNull { it.returnValue }.firstNotNullOfOrNull {
                    resolveToFiltersCall(it)
                }
            }

            is PsiVariable -> {
                element.initializer ?: return null
                return resolveToFiltersCall(element.initializer!!)
            }

            is PsiReferenceExpression -> {
                val referredValue = element.resolve() ?: return null
                return resolveToFiltersCall(referredValue)
            }

            else -> return null
        }
    }

    private fun resolveToUpdatesCall(element: PsiElement): PsiMethodCallExpression? {
        when (element) {
            is PsiMethodCallExpression -> {
                val method = element.resolveMethod() ?: return null
                if (method.containingClass?.qualifiedName == UPDATES_FQN) {
                    return element
                }
                val allReturns = PsiTreeUtil.findChildrenOfType(method.body, PsiReturnStatement::class.java)
                return allReturns.mapNotNull { it.returnValue }.firstNotNullOfOrNull {
                    resolveToUpdatesCall(it)
                }
            }

            is PsiVariable -> {
                element.initializer ?: return null
                return resolveToUpdatesCall(element.initializer!!)
            }

            is PsiReferenceExpression -> {
                val referredValue = element.resolve() ?: return null
                return resolveToUpdatesCall(referredValue)
            }

            else -> return null
        }
    }

    private fun parseUpdatesExpression(filter: PsiMethodCallExpression): Node<PsiElement>? {
        val method = filter.resolveMethod() ?: return null
        if (method.isVarArgs) {
// Updates.combine
            return Node(
                filter,
                listOf(
                    Named(method.name),
                    HasChildren(
                        filter.argumentList.expressions
                            .mapNotNull { resolveToUpdatesCall(it) }
                            .mapNotNull { parseUpdatesExpression(it) },
                    ),
                ),
            )
        } else if (method.parameters.size == 2) {
// If it has two parameters, it's field/value.
            val fieldReference = resolveFieldNameFromExpression(filter.argumentList.expressions[0])
            val valueReference = resolveValueFromExpression(filter.argumentList.expressions[1])

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
        } else if (method.parameters.size == 1) {
// Updates.unset for example
            val fieldReference = resolveFieldNameFromExpression(filter.argumentList.expressions[0])

            return Node(
                filter,
                listOf(
                    Named(method.name),
                    HasFieldReference(
                        fieldReference,
                    ),
                ),
            )
        }
// here we really don't know much, so just don't attempt to parse the query
        return null
    }

    private fun resolveFieldNameFromExpression(expression: PsiExpression): HasFieldReference.FieldReference<out Any> {
        val fieldNameAsString = expression.tryToResolveAsConstantString()
        val fieldReference =
            fieldNameAsString?.let {
                HasFieldReference.Known(expression, it)
            } ?: HasFieldReference.Unknown

        return fieldReference
    }

    private fun resolveValueFromExpression(expression: PsiExpression): HasValueReference.ValueReference<PsiElement> {
        val (wasResolvedAtCompileTime, resolvedValue) = expression.tryToResolveAsConstant()

        val valueReference =
            if (wasResolvedAtCompileTime) {
                HasValueReference.Constant(
                    expression,
                    resolvedValue,
                    resolvedValue?.javaClass.toBsonType()
                )
            } else {
                val psiTypeOfValue =
                    expression
                        .type
                        ?.toBsonType()
                psiTypeOfValue?.let {
                    HasValueReference.Runtime(expression, it)
                } ?: HasValueReference.Unknown
            }
        return valueReference as HasValueReference.ValueReference<PsiElement>
    }

    private fun namespaceComponent(namespace: Namespace?): HasCollectionReference =
        namespace?.let {
            HasCollectionReference(HasCollectionReference.Known(it))
        } ?: HasCollectionReference(HasCollectionReference.Unknown)

    private fun findStartOfQuery(element: PsiElement): PsiMethodCallExpression? {
        val methodCalls = element.findAllChildrenOfType(PsiMethodCallExpression::class.java)
        val bottomLevel: PsiMethodCallExpression = methodCalls.find { methodCall ->
            val method = methodCall.resolveMethod() ?: return@find false
            method.containingClass?.isMongoDbCollectionClass(method.project) == true
        } ?: return null

        return bottomLevel
    }
}
