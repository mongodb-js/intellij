package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.*
import com.mongodb.jbplugin.mql.flattenAnyOfReferences
import com.mongodb.jbplugin.mql.toBsonType

private const val FILTERS_FQN = "com.mongodb.client.model.Filters"
private const val UPDATES_FQN = "com.mongodb.client.model.Updates"

object JavaDriverDialectParser : DialectParser<PsiElement> {
    override fun isCandidateForQuery(source: PsiElement) =
        methodToCommand((source as? PsiMethodCallExpression)?.fuzzyResolveMethod()).type !=
            IsCommand.CommandType.UNKNOWN

    override fun attachment(source: PsiElement): PsiElement = source.findTopParentBy {
        isCandidateForQuery(it)
    }!!

    override fun parse(source: PsiElement): Node<PsiElement> {
        val collectionReference = NamespaceExtractor.extractNamespace(source)

        val currentCall =
            source as? PsiMethodCallExpression ?: return Node(source, listOf(collectionReference))

        val calledMethod = currentCall.fuzzyResolveMethod()
        if (calledMethod?.containingClass?.isMongoDbCollectionClass(source.project) == true) {
            val hasFilters = HasFilter(parseAllFiltersFromCurrentCall(currentCall))
            val hasUpdates = HasUpdates(parseAllUpdatesFromCurrentCall(currentCall))

            return Node(
                source,
                listOf(
                    methodToCommand(calledMethod),
                    collectionReference,
                    hasFilters,
                    hasUpdates
                ),
            )
        } else {
            calledMethod?.let {
                // if it's another class, try to resolve the query from the method body
                val allReturns = PsiTreeUtil.findChildrenOfType(
                    calledMethod.body,
                    PsiReturnStatement::class.java
                )
                return allReturns
                    .mapNotNull { it.returnValue }
                    .flatMap {
                        it.collectTypeUntil(
                            PsiMethodCallExpression::class.java,
                            PsiReturnStatement::class.java
                        )
                    }.firstNotNullOfOrNull {
                        val innerQuery = parse(it)
                        if (!innerQuery.hasComponent<HasFilter<PsiElement>>()) {
                            null
                        } else {
                            innerQuery
                        }
                    } ?: Node(source, listOf(collectionReference, methodToCommand(calledMethod)))
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
        val isString =
            source.parentOfType<PsiLiteralExpression>()?.tryToResolveAsConstantString() != null

        /*
         * IntelliJ might detect that we are not in a string, but in a whitespace (before the string) due to, probably,
         * some internal race conditions. In this case, we will check the parent, which will be an ExpressionList, that
         * will contain all tokens and the string we actually want. Check if any of them is a reference to a field.
         */
        if (source is PsiWhiteSpace) {
            val parentExpressionList = source.parent
            return parentExpressionList.children.any { isReferenceToField(it) }
        }

        return isInQuery && isString
    }

    private fun isInQuery(element: PsiElement): Boolean {
        val methodCall = element.parentOfType<PsiMethodCallExpression>(false) ?: return false
        val containingClass = methodCall.resolveMethod()?.containingClass ?: return false

        return containingClass.qualifiedName == FILTERS_FQN ||
            containingClass.qualifiedName == UPDATES_FQN
    }

    private fun parseFilterExpression(filter: PsiMethodCallExpression): Node<PsiElement>? {
        val method = filter.fuzzyResolveMethod() ?: return null
        if (method.name == "in" || method.name == "nin") {
            if (filter.argumentList.expressionCount == 0) {
                return null // empty, do nothing
            }

            val fieldReference = resolveFieldNameFromExpression(filter.argumentList.expressions[0])
            // if it's only 2 arguments it can be either:
            // - in(field, singleElement) -> valid because of varargs, becomes a single element array
            // - in(field, array) -> valid because of varargs
            // - in(field, iterable) -> valid because of overload
            val valueReference = if (filter.argumentList.expressionCount == 2) {
                var secondArg = filter.argumentList.expressions[1].meaningfulExpression() as PsiExpression
                if (secondArg.type?.isJavaIterable() == true) { // case 3
                    HasValueReference.Runtime(
                        secondArg,
                        BsonArray(
                            secondArg.type?.guessIterableContentType(secondArg.project) ?: BsonAny
                        )
                    )
                } else if (secondArg.type?.isArray() == false) { // case 1
                    val (constant, value) = secondArg.tryToResolveAsConstant()
                    if (constant) {
                        HasValueReference.Constant(
                            secondArg,
                            listOf(value),
                            BsonArray(value?.javaClass.toBsonType(value))
                        )
                    } else {
                        HasValueReference.Runtime(
                            secondArg,
                            BsonArray(
                                secondArg.type?.toBsonType() ?: BsonAny
                            )
                        )
                    }
                } else { // case 2
                    HasValueReference.Runtime(
                        secondArg,
                        secondArg.type?.toBsonType() ?: BsonArray(BsonAny)
                    )
                }
            } else if (filter.argumentList.expressionCount > 2) {
                val allConstants: List<Pair<Boolean, Any?>> = filter.argumentList.expressions.slice(
                    1..<filter.argumentList.expressionCount
                )
                    .map { it.tryToResolveAsConstant() }

                if (allConstants.isEmpty()) {
                    HasValueReference.Runtime(filter, BsonArray(BsonAny))
                } else if (allConstants.all { it.first }) {
                    val eachType = allConstants.mapNotNull {
                        it.second?.javaClass?.toBsonType(it.second)
                    }.map {
                        flattenAnyOfReferences(it)
                    }.toSet()

                    if (eachType.size == 1) {
                        val type = eachType.first()
                        HasValueReference.Constant(
                            filter,
                            allConstants.map { it.second },
                            BsonArray(type)
                        )
                    } else {
                        val eachType = allConstants.mapNotNull {
                            it.second?.javaClass?.toBsonType(it.second)
                        }.toSet()
                        val schema = flattenAnyOfReferences(BsonAnyOf(eachType))
                        HasValueReference.Constant(
                            filter,
                            allConstants.map { it.second },
                            BsonArray(schema)
                        )
                    }
                } else {
                    val eachType = allConstants.mapNotNull {
                        it.second?.javaClass?.toBsonType(it.second)
                    }.toSet()
                    val schema = BsonAnyOf(eachType)
                    HasValueReference.Runtime(
                        filter,
                        BsonArray(schema)
                    )
                }
            } else {
                HasValueReference.Runtime(filter, BsonArray(BsonAny))
            }

            return Node(
                filter,
                listOf(
                    Named(Name.from(method.name)),
                    HasFieldReference(fieldReference),
                    HasValueReference(valueReference)
                ),
            )
        } else if (method.isVarArgs || method.name == "not") {
// Filters.and, Filters.or... are varargs
            return Node(
                filter,
                listOf(
                    Named(Name.from(method.name)),
                    HasFilter(
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
                    Named(Name.from(method.name)),
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
        when (val expression = element.meaningfulExpression()) {
            is PsiMethodCallExpression -> {
                val method = expression.fuzzyResolveMethod() ?: return null
                if (method.containingClass?.qualifiedName == FILTERS_FQN) {
                    return expression
                }
                val allReturns = PsiTreeUtil.findChildrenOfType(
                    method.body,
                    PsiReturnStatement::class.java
                )
                return allReturns.mapNotNull { it.returnValue }.firstNotNullOfOrNull {
                    resolveToFiltersCall(it)
                }
            }

            is PsiVariable -> {
                expression.initializer ?: return null
                return resolveToFiltersCall(expression.initializer!!)
            }

            is PsiReferenceExpression -> {
                val referredValue = expression.resolve() ?: return null
                return resolveToFiltersCall(referredValue)
            }

            else -> return null
        }
    }

    private fun resolveToUpdatesCall(element: PsiElement): PsiMethodCallExpression? {
        when (val expression = element.meaningfulExpression()) {
            is PsiMethodCallExpression -> {
                val method = expression.resolveMethod() ?: return null
                if (method.containingClass?.qualifiedName == UPDATES_FQN) {
                    return expression
                }
                val allReturns = PsiTreeUtil.findChildrenOfType(
                    method.body,
                    PsiReturnStatement::class.java
                )
                return allReturns.mapNotNull { it.returnValue }.firstNotNullOfOrNull {
                    resolveToUpdatesCall(it)
                }
            }

            is PsiVariable -> {
                expression.initializer ?: return null
                return resolveToUpdatesCall(expression.initializer!!)
            }

            is PsiReferenceExpression -> {
                val referredValue = expression.resolve() ?: return null
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
                    Named(Name.from(method.name)),
                    HasFilter(
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
                    Named(Name.from(method.name)),
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
                    Named(Name.from(method.name)),
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

    private fun methodToCommand(method: PsiMethod?): IsCommand {
        return IsCommand(
            when (method?.name) {
                "countDocuments" -> IsCommand.CommandType.COUNT_DOCUMENTS
                "estimatedDocumentCount" -> IsCommand.CommandType.ESTIMATED_DOCUMENT_COUNT
                "distinct" -> IsCommand.CommandType.DISTINCT
                "find" -> IsCommand.CommandType.FIND_MANY
                "first" -> IsCommand.CommandType.FIND_ONE
                "aggregate" -> IsCommand.CommandType.AGGREGATE
                "insertOne" -> IsCommand.CommandType.INSERT_ONE
                "insertMany" -> IsCommand.CommandType.INSERT_MANY
                "deleteOne" -> IsCommand.CommandType.DELETE_ONE
                "deleteMany" -> IsCommand.CommandType.DELETE_MANY
                "replaceOne" -> IsCommand.CommandType.REPLACE_ONE
                "updateOne" -> IsCommand.CommandType.UPDATE_ONE
                "updateMany" -> IsCommand.CommandType.UPDATE_MANY
                "findOneAndDelete" -> IsCommand.CommandType.FIND_ONE_AND_DELETE
                "findOneAndReplace" -> IsCommand.CommandType.FIND_ONE_AND_REPLACE
                "findOneAndUpdate" -> IsCommand.CommandType.FIND_ONE_AND_UPDATE
                else -> IsCommand.CommandType.UNKNOWN
            }
        )
    }
}

private fun PsiType.isArray(): Boolean {
    return this is PsiArrayType
}

private fun PsiType.isJavaIterable(): Boolean {
    if (this !is PsiClassType) {
        return false
    }

    fun recursivelyCheckIsIterable(superType: PsiClassType): Boolean {
        return superType.canonicalText.startsWith("java.lang.Iterable") ||
            superType.superTypes.any {
                it.canonicalText.startsWith("java.lang.Iterable") ||
                    if (it is PsiClassType) {
                        recursivelyCheckIsIterable(it)
                    } else {
                        false
                    }
            }
    }

    return return recursivelyCheckIsIterable(this)
}

private fun PsiType.guessIterableContentType(project: Project): BsonType {
    val text = canonicalText
    val start = text.indexOf('<')
    if (start == -1) {
        return BsonAny
    }
    val end = text.indexOf('>', startIndex = start)
    if (end == -1) {
        return BsonAny
    }

    val typeStr = text.substring(start + 1, end)
    return PsiType.getTypeByName(
        typeStr,
        project,
        GlobalSearchScope.everythingScope(project)
    ).toBsonType()
}
