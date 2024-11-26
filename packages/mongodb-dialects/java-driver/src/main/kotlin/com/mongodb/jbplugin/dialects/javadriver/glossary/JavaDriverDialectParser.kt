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
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.ComputedBsonType
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.*
import com.mongodb.jbplugin.mql.components.HasFieldReference.Computed
import com.mongodb.jbplugin.mql.components.HasFieldReference.FromSchema
import com.mongodb.jbplugin.mql.flattenAnyOfReferences
import com.mongodb.jbplugin.mql.toBsonType

private const val COLLECTION_FQN = "com.mongodb.client.MongoCollection"
private const val SESSION_FQN = "com.mongodb.client.ClientSession"
private const val FILTERS_FQN = "com.mongodb.client.model.Filters"
private const val UPDATES_FQN = "com.mongodb.client.model.Updates"
private const val AGGREGATES_FQN = "com.mongodb.client.model.Aggregates"
private const val PROJECTIONS_FQN = "com.mongodb.client.model.Projections"
private const val ACCUMULATORS_FQN = "com.mongodb.client.model.Accumulators"
private const val SORTS_FQN = "com.mongodb.client.model.Sorts"
private const val FIELD_FQN = "com.mongodb.client.model.Field"
private const val JAVA_LIST_FQN = "java.util.List"
private const val JAVA_ARRAYS_FQN = "java.util.Arrays"

private val PARSEABLE_AGGREGATION_STAGE_METHODS = listOf(
    "match",
    "project",
    "sort",
    "group",
    "unwind",
    "addFields"
)

object JavaDriverDialectParser : DialectParser<PsiElement> {
    override fun isCandidateForQuery(source: PsiElement) =
        methodCallToCommand((source as? PsiMethodCallExpression)).type !=
            IsCommand.CommandType.UNKNOWN

    override fun attachment(source: PsiElement): PsiElement = source.findTopParentBy {
        isCandidateForQuery(it)
    }!!

    override fun parse(source: PsiElement): Node<PsiElement> {
        val sourceDialect = HasSourceDialect(HasSourceDialect.DialectName.JAVA_DRIVER)
        val collectionReference = NamespaceExtractor.extractNamespace(source)

        val currentCall =
            source as? PsiMethodCallExpression
                ?: return Node(source, listOf(sourceDialect, collectionReference))
        val command = methodCallToCommand(currentCall)

        /**
         * We might come across a FIND_ONE command and in that case we need to be pointing to the
         * right method call, find() and not find().first(), in order to parse the filter arguments
         */
        val commandCall = currentCall.findMongoDbCollectionMethodCallForCommand(command)
        val commandCallMethod = commandCall.fuzzyResolveMethod()

        if (commandCallMethod?.containingClass?.isMongoDbCollectionClass(source.project) == true) {
            val hasFilters = HasFilter(parseAllFiltersFromCurrentCall(commandCall))
            val hasUpdates = HasUpdates(parseAllUpdatesFromCurrentCall(commandCall))
            val hasAggregation = HasAggregation(parseAggregationStagesFromCurrentCall(commandCall))
            return Node(
                source,
                listOf(
                    sourceDialect,
                    command,
                    collectionReference,
                    hasFilters,
                    hasUpdates,
                    hasAggregation,
                ),
            )
        } else {
            commandCallMethod?.let {
                // if it's another class, try to resolve the query from the method body
                val allReturns = PsiTreeUtil.findChildrenOfType(
                    commandCallMethod.body,
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
                    }
                    ?: Node(
                        source,
                        listOf(
                            sourceDialect,
                            collectionReference,
                            command
                        )
                    )
            } ?: return Node(source, listOf(sourceDialect, collectionReference))
        }
    }

    private fun parseAllFiltersFromCurrentCall(currentCall: PsiMethodCallExpression): List<Node<PsiElement>> {
        if (currentCall.argumentList.expressionCount > 0) {
            // TODO: we might want to have a component that tells this query is in a transaction
            val startIndex = if (hasMongoDbSessionReference(currentCall)) 1 else 0
            val filterExpression = currentCall.argumentList.expressions.getOrNull(startIndex)
                ?: return emptyList()

            // we have at least 1 argument in the current method call
            // try to get the relevant filter calls, or avoid parsing the query at all
            val argumentAsFilters = resolveBsonBuilderCall(filterExpression, FILTERS_FQN)
            return argumentAsFilters?.let {
                val parsedQuery = parseFilterExpression(argumentAsFilters)
                parsedQuery?.let {
                    listOf(
                        parsedQuery,
                    )
                } ?: emptyList()
            } ?: emptyList()
        } else {
            return emptyList()
        }
    }

    private fun parseAggregationStagesFromCurrentCall(
        currentCall: PsiMethodCallExpression
    ): List<Node<PsiElement>> {
        // Ensure current call is Aggregates.aggregate
        val currentCallMethod = currentCall.fuzzyResolveMethod()
        val isAggregateCall = currentCallMethod?.name == "aggregate" &&
            currentCallMethod.containingClass?.qualifiedName == COLLECTION_FQN

        if (!isAggregateCall || currentCall.argumentList.expressionCount == 0) {
            return emptyList()
        }

        // Assuming we're parsing Aggregates.aggregate(List<Bson>) variant of aggregate
        val stageListExpression = currentCall.argumentList.expressions.getOrNull(0)
            ?: return emptyList()

        val aggregationStageCalls = resolveToAggregationStageCalls(stageListExpression)
        return aggregationStageCalls.mapNotNull(::parseAggregationStage)
    }

    private fun parseAllUpdatesFromCurrentCall(currentCall: PsiMethodCallExpression): List<Node<PsiElement>> {
        if (currentCall.argumentList.expressionCount > 1) {
            // TODO: we might want to have a component that tells this query is in a transaction
            val startIndex = if (hasMongoDbSessionReference(currentCall)) 2 else 1
            val updateExpression = currentCall.argumentList.expressions.getOrNull(startIndex)
                ?: return emptyList()

            val argumentAsUpdates = resolveBsonBuilderCall(updateExpression, UPDATES_FQN)
            // parse only if it's a call to `updates` methods
            return argumentAsUpdates?.let {
                val parsedQuery = parseUpdatesExpression(argumentAsUpdates)
                parsedQuery?.let {
                    listOf(parsedQuery)
                } ?: emptyList()
            } ?: emptyList()
        } else {
            return emptyList()
        }
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
        val isInAggregate = isInAutoCompletableAggregation(source)
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

        return (isInQuery || isInAggregate) && isString
    }

    private fun isInQuery(element: PsiElement): Boolean {
        val methodCall = element.parentOfType<PsiMethodCallExpression>(false) ?: return false
        val containingClass = methodCall.resolveMethod()?.containingClass ?: return false

        return containingClass.qualifiedName == FILTERS_FQN ||
            containingClass.qualifiedName == UPDATES_FQN
    }

    private fun isInAutoCompletableAggregation(element: PsiElement): Boolean {
        val methodCall = element.parentOfType<PsiMethodCallExpression>(false) ?: return false
        val containingClass = methodCall.resolveMethod()?.containingClass ?: return false
        if (
            containingClass.qualifiedName == PROJECTIONS_FQN ||
            containingClass.qualifiedName == SORTS_FQN ||
            containingClass.qualifiedName == ACCUMULATORS_FQN
        ) {
            return isInAutoCompletableAggregation(methodCall)
        }

        return containingClass.qualifiedName == AGGREGATES_FQN &&
            // AddFields does not benefit from autocomplete of fields. We might to change this when
            // we start parsing Value expressions as well.
            methodCall.fuzzyResolveMethod()?.name != "addFields"
    }

    private fun resolveBsonBuilderCall(
        element: PsiElement,
        classQualifiedName: String
    ): PsiMethodCallExpression? {
        return element.resolveToMethodCallExpression { _, methodCall ->
            methodCall.containingClass?.qualifiedName == classQualifiedName
        }
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
                val secondArg = filter.argumentList.expressions[1].meaningfulExpression() as PsiExpression
                if (secondArg.type?.isJavaIterable() == true) { // case 3
                    filter.argumentList.inferFromSingleVarArgElement(start = 1)
                } else if (secondArg.type?.isArray() == false) { // case 1
                    filter.argumentList.inferFromSingleArrayArgument(start = 1)
                } else { // case 2
                    HasValueReference.Runtime(
                        secondArg,
                        secondArg.type?.toBsonType() ?: BsonArray(BsonAny)
                    )
                }
            } else if (filter.argumentList.expressionCount > 2) {
                filter.argumentList.inferValueReferenceFromVarArg(start = 1)
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
                            .mapNotNull { resolveBsonBuilderCall(it, FILTERS_FQN) }
                            .mapNotNull { parseFilterExpression(it) },
                    ),
                ),
            )
        } else if (method.name == "eq" && method.parameters.size == 1) {
            if (filter.argumentList.expressionCount == 0) {
                return null
            }
            val valueExpression = filter.argumentList.expressions[0]
            val valueReference = resolveValueFromExpression(valueExpression)
            val fieldReference = FromSchema(valueExpression, "_id")

            return Node(
                filter,
                listOf(
                    Named(Name.from(method.name)),
                    HasFieldReference(fieldReference),
                    HasValueReference(valueReference),
                )
            )
        } else if (method.name == "exists" && method.parameters.size == 1) {
            if (filter.argumentList.expressionCount == 0) {
                return null
            }
            val fieldExpression = filter.argumentList.expressions[0]
            val fieldReference = resolveFieldNameFromExpression(fieldExpression)
            val valueReference = HasValueReference.Inferred(
                source = fieldExpression,
                value = true,
                type = BsonBoolean,
            )

            return Node(
                filter,
                listOf(
                    Named(Name.from(method.name)),
                    HasFieldReference(fieldReference),
                    HasValueReference(valueReference),
                )
            )
        } else if (method.parameters.size == 2) {
            // If it has two parameters, it's field/value.
            val fieldReference = resolveFieldNameFromExpression(filter.argumentList.expressions[0])
            val valueReference = resolveValueFromExpression(filter.argumentList.expressions[1])

            return Node(
                filter,
                listOf(
                    Named(Name.from(method.name)),
                    HasFieldReference(fieldReference),
                    HasValueReference(valueReference),
                ),
            )
        }
        // here we really don't know much, so just don't attempt to parse the query
        return null
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
                            .mapNotNull { resolveBsonBuilderCall(it, UPDATES_FQN) }
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

    private fun resolveToAggregationStageCalls(maybeIterableElement: PsiElement): List<PsiMethodCallExpression> {
        val iterableCallExpression = maybeIterableElement.resolveToIterableCallExpression()
            ?: return emptyList()

        return iterableCallExpression.argumentList.expressions.mapNotNull {
            it.resolveToMethodCallExpression { _, methodCall ->
                isAggregationStageMethodCall(methodCall)
            }
        }
    }

    private fun parseAggregationStage(stageCall: PsiMethodCallExpression): Node<PsiElement>? {
        // Ensure it is a valid stage call
        val stageCallMethod = stageCall.fuzzyResolveMethod() ?: return null

        if (!isAggregationStageMethodCall(stageCallMethod)) {
            return null
        }

        when (stageCallMethod.name) {
            "match" -> {
                val nodeWithFilters: (List<Node<PsiElement>>) -> Node<PsiElement> =
                    { filter ->
                        Node(
                            source = stageCall,
                            components = listOf(
                                Named(Name.MATCH),
                                HasFilter(filter)
                            )
                        )
                    }
                // There will only be one argument to Aggregates.match and that has to be the Bson
                // filters. We retrieve that and resolve the values.
                val filterExpression = stageCall.argumentList.expressions.getOrNull(0)
                    ?: return nodeWithFilters(emptyList())

                val resolvedFilterExpression = resolveBsonBuilderCall(
                    filterExpression,
                    FILTERS_FQN,
                ) ?: return nodeWithFilters(emptyList())

                val parsedFilter = parseFilterExpression(resolvedFilterExpression)
                    ?: return nodeWithFilters(emptyList())

                return Node(
                    source = stageCall,
                    components = listOf(
                        Named(Name.MATCH),
                        HasFilter(listOf(parsedFilter))
                    )
                )
            }

            "project",
            "sort" -> {
                val stageName = Name.from(stageCallMethod.name)
                val nodeWithParsedComponents: (List<Node<PsiElement>>) -> Node<PsiElement> =
                    { components ->
                        Node(
                            source = stageCall,
                            components = listOf(
                                Named(stageName),
                                if (stageName == Name.PROJECT) {
                                    HasProjections(components)
                                } else {
                                    HasSorts(components)
                                }
                            )
                        )
                    }

                // There will only be one argument to Aggregates.project and Aggregates.sort and
                // that has to be the Bson projections or sorts. We retrieve that and resolve the
                // values.
                val bsonBuilderExpression = stageCall.argumentList.expressions.getOrNull(0)
                    ?: return nodeWithParsedComponents(emptyList())

                val resolvedBsonBuilderExpression = resolveBsonBuilderCall(
                    bsonBuilderExpression,
                    if (stageName == Name.PROJECT) PROJECTIONS_FQN else SORTS_FQN,
                ) ?: return nodeWithParsedComponents(emptyList())

                val parsedComponents = parseBsonBuilderCallsSimilarToProjections(
                    resolvedBsonBuilderExpression,
                    if (stageName == Name.PROJECT) PROJECTIONS_FQN else SORTS_FQN,
                )

                return nodeWithParsedComponents(parsedComponents)
            }
            "group" -> {
                // the first parameter of group is going to be a string expression
                val groupArgument = stageCall.argumentList.expressions.getOrNull(0)
                    ?: return null

                val groupFieldValueExpression = parseComputedExpression(groupArgument)

                val nodeWithAccumulators: (List<Node<PsiElement>>) -> Node<PsiElement> = { accFields: List<Node<PsiElement>> ->
                    Node(
                        stageCall,
                        listOf(
                            Named(Name.GROUP),
                            HasFieldReference(
                                HasFieldReference.Inferred(groupArgument, "_id", "_id")
                            ),
                            groupFieldValueExpression,
                            HasAccumulatedFields(accFields)
                        )
                    )
                }

                val accumulators = stageCall.getVarArgsOrIterableArgs().drop(1)
                    .mapNotNull { resolveBsonBuilderCall(it, ACCUMULATORS_FQN) }

                val parsedAccumulators = accumulators.mapNotNull { parseAccumulatorExpression(it) }
                return nodeWithAccumulators(parsedAccumulators)
            }

            "addFields" -> {
                // .addFields can have varargs of Field objects or Fields objects in an iterable
                // so we need to resolve them.
                val addedFieldNodes = resolveAddFieldsArguments(stageCall)
                    .mapNotNull(::parseAddFieldsArgument)

                return Node(
                    source = stageCall,
                    components = listOf(
                        Named(Name.ADD_FIELDS),
                        HasAddedFields(addedFieldNodes),
                    )
                )
            }

            "unwind" -> {
                val fieldExpression = stageCall.argumentList.expressions.getOrNull(0)
                    ?: return Node(
                        source = stageCall,
                        components = listOf(
                            Named(Name.UNWIND)
                        )
                    )

                val fieldName = fieldExpression.tryToResolveAsConstantString()
                    ?: return Node(
                        source = stageCall,
                        components = listOf(
                            Named(Name.UNWIND),
                            HasFieldReference(
                                HasFieldReference.Unknown
                            )
                        )
                    )

                return Node(
                    source = stageCall,
                    components = listOf(
                        Named(Name.UNWIND),
                        HasFieldReference(
                            HasFieldReference.FromSchema(
                                source = fieldExpression,
                                fieldName = fieldName.trim('$'),
                                displayName = fieldName,
                            )
                        ),
                    )
                )
            }

            else -> return null
        }
    }

    private fun parseBsonBuilderCallsSimilarToProjections(
        expression: PsiMethodCallExpression,
        classQualifiedName: String
    ): List<Node<PsiElement>> {
        val methodCall = expression.resolveMethod() ?: return emptyList()
        return when (methodCall.name) {
            "fields",
            "orderBy" -> expression.getVarArgsOrIterableArgs()
                .mapNotNull { resolveBsonBuilderCall(it, classQualifiedName) }
                .flatMap { parseBsonBuilderCallsSimilarToProjections(it, classQualifiedName) }

            "include",
            "exclude",
            "ascending",
            "descending" -> expression.getVarArgsOrIterableArgs()
                .mapNotNull {
                    val fieldReference = resolveFieldNameFromExpression(it)
                    val methodName = Name.from(methodCall.name)
                    when (fieldReference) {
                        is FromSchema -> Node(
                            source = it,
                            components = listOf(
                                Named(methodName),
                                HasFieldReference(fieldReference),
                                HasValueReference(
                                    reference = HasValueReference.Inferred(
                                        source = it,
                                        value = when (methodName) {
                                            Name.INCLUDE,
                                            Name.ASCENDING -> 1
                                            Name.EXCLUDE -> 0
                                            // Else is just the descending case
                                            else -> -1
                                        },
                                        type = BsonInt32,
                                    )
                                )
                            )
                        )
                        else -> null
                    }
                }

            else -> emptyList()
        }
    }

    private fun parseAccumulatorExpression(expression: PsiMethodCallExpression): Node<PsiElement>? {
        val methodCall = expression.fuzzyResolveMethod() ?: return null

        return when (methodCall.name) {
            "sum" -> parseKeyValAccumulator(expression, Name.SUM)
            "avg" -> parseKeyValAccumulator(expression, Name.AVG)
            "first" -> parseKeyValAccumulator(expression, Name.FIRST)
            "last" -> parseKeyValAccumulator(expression, Name.LAST)
            "top" -> parseLeadingAccumulatorExpression(expression, Name.TOP)
            "bottom" -> parseLeadingAccumulatorExpression(expression, Name.BOTTOM)
            "max" -> parseKeyValAccumulator(expression, Name.MAX)
            "min" -> parseKeyValAccumulator(expression, Name.MIN)
            "push" -> parseKeyValAccumulator(expression, Name.PUSH)
            "addToSet" -> parseKeyValAccumulator(expression, Name.ADD_TO_SET)
            else -> null
        }
    }

    private fun parseKeyValAccumulator(expression: PsiMethodCallExpression, name: Name): Node<PsiElement>? {
        val keyExpr = expression.argumentList.expressions.getOrNull(0) ?: return null
        val valueExpr = expression.argumentList.expressions.getOrNull(1) ?: return null

        val fieldName = keyExpr.tryToResolveAsConstantString()
        val accumulatorExpr = parseComputedExpression(valueExpr)

        return Node(
            expression,
            listOf(
                Named(name),
                HasFieldReference(
                    if (fieldName != null) {
                        Computed(keyExpr, fieldName, fieldName)
                    } else {
                        HasFieldReference.Unknown as HasFieldReference.FieldReference<PsiElement>
                    }
                ),
                accumulatorExpr
            )
        )
    }

    private fun parseLeadingAccumulatorExpression(expression: PsiMethodCallExpression, name: Name): Node<PsiElement>? {
        val keyExpr = expression.argumentList.expressions.getOrNull(0) ?: return null
        val sortExprArgument = expression.argumentList.expressions.getOrNull(1) ?: return null
        val valueExpr = expression.argumentList.expressions.getOrNull(2) ?: return null

        val sortExpr = resolveBsonBuilderCall(sortExprArgument, SORTS_FQN) ?: return null

        val fieldName = keyExpr.tryToResolveAsConstantString()
        val sort = parseBsonBuilderCallsSimilarToProjections(sortExpr, SORTS_FQN)
        val accumulatorExpr = parseComputedExpression(valueExpr)

        return Node(
            expression,
            listOf(
                Named(name),
                HasFieldReference(
                    if (fieldName != null) {
                        Computed(keyExpr, fieldName, fieldName)
                    } else {
                        HasFieldReference.Unknown as HasFieldReference.FieldReference<PsiElement>
                    }
                ),
                HasSorts(sort),
                accumulatorExpr
            )
        )
    }

    private fun resolveAddFieldsArguments(
        addFieldsCall: PsiMethodCallExpression
    ): List<PsiNewExpression> {
        return addFieldsCall.getVarArgsOrIterableArgs()
            .mapNotNull {
                it.resolveElementUntil<PsiNewExpression> { element ->
                    val newExpression = element as? PsiNewExpression
                        ?: return@resolveElementUntil false

                    val classReference = (newExpression.classReference?.resolve() as? PsiClass)
                        ?: return@resolveElementUntil false

                    return@resolveElementUntil classReference.qualifiedName == FIELD_FQN
                }
            }
    }

    private fun parseAddFieldsArgument(fieldExpression: PsiNewExpression): Node<PsiElement>? {
        val expressionArguments = fieldExpression.argumentList?.expressions
        val fieldNameExpression = expressionArguments?.getOrNull(0)
        val fieldReference = fieldNameExpression?.tryToResolveAsConstantString()?.let {
            HasFieldReference.Computed(
                source = fieldNameExpression,
                fieldName = it,
            )
        } ?: HasFieldReference.Unknown

        val valueExpression = expressionArguments?.getOrNull(1)
        val valueReference = valueExpression?.tryToResolveAsConstantString()?.let {
            // We currently only support parsing a constant value which is why we will encode
            // anything else, not parseable, as an Unknown.
            HasValueReference.Constant(
                source = valueExpression,
                value = it,
                type = it.javaClass.toBsonType(),
            )
        } ?: HasValueReference.Unknown

        return Node(
            source = fieldExpression,
            components = listOf(
                HasFieldReference(fieldReference),
                HasValueReference(valueReference)
            )
        )
    }

    private fun hasMongoDbSessionReference(methodCall: PsiMethodCallExpression): Boolean {
        val hasEnoughArgs = methodCall.argumentList.expressionCount > 0
        if (!hasEnoughArgs) {
            return false
        }

        val typeOfFirstArg = methodCall.argumentList.expressionTypes[0]
        return typeOfFirstArg != null && typeOfFirstArg.equalsToText(SESSION_FQN)
    }

    private fun parseComputedExpression(element: PsiElement): HasValueReference<PsiElement> {
        val (constant, value) = element.tryToResolveAsConstant()
        return HasValueReference(
            when {
                constant && value is String -> HasValueReference.Computed(
                    element,
                    type = ComputedBsonType(
                        BsonAny,
                        Node(
                            element,
                            listOf(
                                HasFieldReference(
                                    FromSchema(element, value.trim('$'), value)
                                )
                            )
                        )
                    )
                )
                constant -> HasValueReference.Constant(
                    element,
                    value,
                    value?.javaClass.toBsonType(value)
                )
                !constant && element is PsiExpression -> HasValueReference.Runtime(
                    element,
                    element.type?.toBsonType() ?: BsonAny
                )
                else -> HasValueReference.Unknown as HasValueReference.ValueReference<PsiElement>
            }
        )
    }

    private fun isAggregationStageMethodCall(callMethod: PsiMethod?): Boolean {
        return PARSEABLE_AGGREGATION_STAGE_METHODS.contains(callMethod?.name) &&
            callMethod?.containingClass?.qualifiedName == AGGREGATES_FQN
    }

    private fun resolveFieldNameFromExpression(expression: PsiExpression): HasFieldReference.FieldReference<out Any> {
        val fieldNameAsString = expression.tryToResolveAsConstantString()
        val fieldReference =
            fieldNameAsString?.let {
                FromSchema(expression, it)
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

    private fun methodCallToCommand(methodCall: PsiMethodCallExpression?): IsCommand {
        if (methodCall == null) {
            return IsCommand(IsCommand.CommandType.UNKNOWN)
        }

        val method = methodCall.fuzzyResolveMethod()

        if (method?.containingClass?.qualifiedName?.contains("MongoIterable") == true) {
            // We are in a cursor, so the actual operation is in the upper method calls
            // For context, the current call is somewhat like this:
            //   MongoCollection.find(Filters.eq(...)).first()
            // and to correctly identify the command we need to be analysing
            //   MongoCollection.find(Filters.eq(...))
            val allCallExpressions = methodCall.findAllChildrenOfType(
                PsiMethodCallExpression::class.java
            )
            val lastCallExpression = allCallExpressions.getOrNull(allCallExpressions.lastIndex - 1)
            val result = methodCallToCommand(lastCallExpression)

            // FindIterable.first() translates to a valid MongoDB driver command so we need to take
            // that into account and return correct command result. Because we analysed the earlier
            // chained call using the lastCallExpression, the result in this case will be FIND_MANY
            if (result.type == IsCommand.CommandType.FIND_MANY && method.name == "first") {
                return IsCommand(IsCommand.CommandType.FIND_ONE)
            }

            // If we come across a .first() call on any iterable other than a FindIterable then for
            // that particular MethodCallExpression we can safely say that the command is not known.
            if (method.name == "first") {
                return IsCommand(IsCommand.CommandType.UNKNOWN)
            }
        }

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

fun PsiExpressionList.inferFromSingleArrayArgument(start: Int = 0): HasValueReference.ValueReference<PsiElement> {
    val arrayArg = expressions[start]
    val (constant, value) = arrayArg.tryToResolveAsConstant()

    return if (constant) {
        HasValueReference.Constant(
            arrayArg,
            listOf(value),
            BsonArray(value?.javaClass.toBsonType(value))
        )
    } else {
        HasValueReference.Runtime(
            arrayArg,
            BsonArray(
                arrayArg.type?.toBsonType() ?: BsonAny
            )
        )
    }
}

fun PsiType.isArray(): Boolean {
    return this is PsiArrayType
}

fun PsiType.isJavaIterable(): Boolean {
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

    return recursivelyCheckIsIterable(this)
}

fun PsiType.guessIterableContentType(project: Project): BsonType {
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

fun PsiExpressionList.inferValueReferenceFromVarArg(start: Int = 0): HasValueReference.ValueReference<PsiElement> {
    val allConstants: List<Pair<Boolean, Any?>> = expressions.slice(
        start..<expressionCount
    ).map { it.tryToResolveAsConstant() }

    if (allConstants.isEmpty()) {
        return HasValueReference.Runtime(parent, BsonArray(BsonAny))
    } else if (allConstants.all { it.first }) {
        val eachType = allConstants.mapNotNull {
            it.second?.javaClass?.toBsonType(it.second)
        }.map {
            flattenAnyOfReferences(it)
        }.toSet()

        if (eachType.size == 1) {
            val type = eachType.first()
            return HasValueReference.Constant(
                parent,
                allConstants.map { it.second },
                BsonArray(type)
            )
        } else {
            val eachType = allConstants.mapNotNull {
                it.second?.javaClass?.toBsonType(it.second)
            }.toSet()
            val schema = flattenAnyOfReferences(BsonAnyOf(eachType))
            return HasValueReference.Constant(
                parent,
                allConstants.map { it.second },
                BsonArray(schema)
            )
        }
    } else {
        return HasValueReference.Runtime(parent, BsonArray(BsonAny))
    }
}

fun PsiExpressionList.inferFromSingleVarArgElement(start: Int = 0): HasValueReference.ValueReference<PsiElement> {
    var secondArg = expressions[start].meaningfulExpression() as PsiExpression
    return if (secondArg.type?.isJavaIterable() == true) { // case 3
        HasValueReference.Runtime(
            secondArg,
            BsonArray(
                secondArg.type?.guessIterableContentType(secondArg.project) ?: BsonAny
            )
        )
    } else {
        HasValueReference.Runtime(parent, BsonArray(BsonAny))
    }
}

/**
 * Helper method to retrieve the actual arguments passed to a method call that can receive both
 * var-args and also the iterable args created using List.of, Arrays.asList. Example:
 * ```
 * Projections.project(projection1, projection2)
 * ```
 * and
 * ```
 * Projections.project(List.of(projection1, projection2))
 * ```
 * Arguments passed to both the overloads can be retrieved using this method
 */
fun PsiMethodCallExpression.getVarArgsOrIterableArgs(): List<PsiExpression> {
    val methodCall = fuzzyResolveMethod() ?: return emptyList()
    if (methodCall.isVarArgs) {
        return argumentList.expressions.asList()
    } else {
        val maybeIterableExpression = argumentList.expressions.getOrNull(0)
            ?: return emptyList()

        val iterableCallExpression = maybeIterableExpression.resolveToIterableCallExpression()
            ?: return emptyList()

        return iterableCallExpression.argumentList.expressions.asList()
    }
}

/**
 * Helper method to resolve an expression that points to an iterable, to its actual
 * MethodCallExpression that was used to create iterable. Particularly it targets iterables created
 * using `List.of` and `Arrays.asList` methods.
 */
fun PsiElement.resolveToIterableCallExpression(): PsiMethodCallExpression? {
    return resolveToMethodCallExpression { _, method ->
        val isListOfCall = method.name == "of" &&
            method.containingClass?.qualifiedName == JAVA_LIST_FQN

        val isArrayAsListCall = method.name == "asList" &&
            method.containingClass?.qualifiedName == JAVA_ARRAYS_FQN

        isListOfCall || isArrayAsListCall
    }
}

/**
 * Helper method to resolve an expression to its original MethodCallExpression using the predicate
 * provided at the call site.
 */
fun PsiElement.resolveToMethodCallExpression(
    isCorrectMethodCall: (PsiMethodCallExpression, PsiMethod) -> Boolean
): PsiMethodCallExpression? {
    return this.resolveElementUntil { element ->
        val callExpression = element as? PsiMethodCallExpression
            ?: return@resolveElementUntil false
        val methodCall = callExpression.fuzzyResolveMethod()
            ?: return@resolveElementUntil false
        return@resolveElementUntil isCorrectMethodCall(callExpression, methodCall)
    }
}

fun <T : PsiElement>PsiElement.resolveElementUntil(
    isCorrectResolution: (PsiElement) -> Boolean
): T? {
    val expression = meaningfulExpression()
    if (isCorrectResolution(expression)) {
        return expression as T
    }
    when (expression) {
        is PsiMethodCallExpression -> {
            val methodCall = expression.fuzzyResolveMethod() ?: return null
            return PsiTreeUtil.findChildrenOfType(
                methodCall.body,
                PsiReturnStatement::class.java
            )
                .mapNotNull { it.returnValue }
                .firstNotNullOfOrNull { it.resolveElementUntil(isCorrectResolution) }
        }

        is PsiVariable -> {
            return expression.initializer?.resolveElementUntil(isCorrectResolution)
        }

        is PsiReferenceExpression -> {
            return expression.resolve()?.resolveElementUntil(isCorrectResolution)
        }

        else -> return null
    }
}
