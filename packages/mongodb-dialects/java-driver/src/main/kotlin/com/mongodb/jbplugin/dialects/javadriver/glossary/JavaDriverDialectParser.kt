package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.allArguments
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.argumentAt
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.classIs
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.containingClass
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.meaningfulExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.methodCall
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.methodCallChain
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.methodName
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.methodReturnStatements
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.referenceExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.resolveMethod
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.toFieldReference
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.toValueFromArgumentList
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.toValueReference
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.variable
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.HasUpdates
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import com.mongodb.jbplugin.mql.flattenAnyOfReferences
import com.mongodb.jbplugin.mql.parser.Parser
import com.mongodb.jbplugin.mql.parser.acceptAnyError
import com.mongodb.jbplugin.mql.parser.allMatching
import com.mongodb.jbplugin.mql.parser.cond
import com.mongodb.jbplugin.mql.parser.constant
import com.mongodb.jbplugin.mql.parser.equalsTo
import com.mongodb.jbplugin.mql.parser.filter
import com.mongodb.jbplugin.mql.parser.first
import com.mongodb.jbplugin.mql.parser.firstMatching
import com.mongodb.jbplugin.mql.parser.firstResult
import com.mongodb.jbplugin.mql.parser.flatMap
import com.mongodb.jbplugin.mql.parser.inputAs
import com.mongodb.jbplugin.mql.parser.lateInit
import com.mongodb.jbplugin.mql.parser.map
import com.mongodb.jbplugin.mql.parser.mapAs
import com.mongodb.jbplugin.mql.parser.mapMany
import com.mongodb.jbplugin.mql.parser.matches
import com.mongodb.jbplugin.mql.parser.matchesAny
import com.mongodb.jbplugin.mql.parser.otherwiseParse
import com.mongodb.jbplugin.mql.parser.requireNonNull
import com.mongodb.jbplugin.mql.parser.zip
import com.mongodb.jbplugin.mql.toBsonType
import kotlinx.coroutines.runBlocking

private const val ITERABLE_FQN = "com.mongodb.client.MongoIterable"
private const val SESSION_FQN = "com.mongodb.client.ClientSession"
private const val FILTERS_FQN = "com.mongodb.client.model.Filters"
private const val UPDATES_FQN = "com.mongodb.client.model.Updates"

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

        val calledMethod = currentCall.fuzzyResolveMethod()
        if (calledMethod?.containingClass?.isMongoDbCollectionClass(source.project) == true) {
            val hasFilters = HasFilter(parseAllFiltersFromCurrentCall(currentCall))
            val hasUpdates = HasUpdates(parseAllUpdatesFromCurrentCall(currentCall))

            return Node(
                source,
                listOf(
                    sourceDialect,
                    methodCallToCommand(currentCall),
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
                    }
                    ?: Node(
                        source,
                        listOf(
                            sourceDialect,
                            collectionReference,
                            methodCallToCommand(currentCall)
                        )
                    )
            } ?: return Node(source, listOf(sourceDialect, collectionReference))
        }
    }

    private fun parseAllFiltersFromCurrentCall(currentCall: PsiMethodCallExpression): List<Node<PsiElement>> {
        if (currentCall.argumentList.expressionCount > 0) {
            // TODO: we might want to have a component that tells this query is in a transaction
            val startIndex = if (hasMongoDbSessionReference(currentCall)) 1 else 0
            var filterExpression = currentCall.argumentList.expressions.getOrNull(startIndex)

            if (filterExpression == null) {
                return emptyList()
            }

            // we have at least 1 argument in the current method call
            // try to get the relevant filter calls, or avoid parsing the query at all
            val argumentAsFilters = resolveToFiltersCall(filterExpression)
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

    private fun parseAllUpdatesFromCurrentCall(currentCall: PsiMethodCallExpression): List<Node<PsiElement>> {
        if (currentCall.argumentList.expressionCount > 1) {
            // TODO: we might want to have a component that tells this query is in a transaction
            val startIndex = if (hasMongoDbSessionReference(currentCall)) 2 else 1
            var updateExpression = currentCall.argumentList.expressions.getOrNull(startIndex)

            if (updateExpression == null) {
                return emptyList()
            }

            val argumentAsUpdates = resolveToUpdatesCall(updateExpression)
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
        val parseFilterExpression = lateInit<PsiElement, Any, Node<PsiElement>>()

        val whenInAInOrNinMethod = methodCall()
            .matches(
                resolveMethod().flatMap(methodName())
                    .filter { it == "in" || it == "nin" }
                    .matches()
            )
            .acceptAnyError()

        val whenHasTwoArguments = methodCall()
            .filter { it.argumentList.expressionCount == 2 }

        val whenInOrNinUsesASingleElementValue = whenInAInOrNinMethod
            .matches(whenHasTwoArguments.matches())
            .flatMap(argumentAt(1))

        val whenSecondArgumentIsJavaIterable = whenInOrNinUsesASingleElementValue
            .mapAs<PsiExpression, _, _, _>()
            .filter { it.type?.isJavaIterable() == true }
            .flatMap(toValueReference(isArrayElement = false))
            .acceptAnyError()

        val whenSecondArgumentIsJavaArray = whenInOrNinUsesASingleElementValue
            .mapAs<PsiExpression, _, _, _>()
            .filter { it.type?.isArray() == true }
            .flatMap(toValueReference(isArrayElement = false))
            .acceptAnyError()

        val whenSecondArgumentIsASingleObject = whenInOrNinUsesASingleElementValue
            .flatMap(toValueReference(isArrayElement = true))
            .acceptAnyError()

        val varargInOrNinParser = whenInAInOrNinMethod
            .matches(resolveMethod().filter { it.isVarArgs }.matches())
            .zip(resolveMethod().map { it.name })
            .zip(argumentAt(0).flatMap(toFieldReference()))
            .zip(toValueFromArgumentList(start = 1))
            .map {
                Node(
                    it.first.first.first as PsiElement,
                    listOf(
                        Named(Name.from(it.first.first.second)),
                        it.first.second,
                        it.second
                    ),
                )
            }.acceptAnyError()

        val fixedArgumentsInOrNinParser = methodCall()
            .zip(
                first(
                    whenSecondArgumentIsJavaIterable,
                    whenSecondArgumentIsJavaArray,
                    whenSecondArgumentIsASingleObject
                )
            ).zip(argumentAt(0).flatMap(toFieldReference()))
            .zip(resolveMethod().flatMap(methodName()))
            .map {
                Node(
                    it.first.first.first as PsiElement,
                    listOf(
                        Named(Name.from(it.second)),
                        it.first.second,
                        it.first.first.second
                    ),
                )
            }.acceptAnyError()

        val isLogicalVarArg = methodCall()
            .matchesAny(
                resolveMethod().filter { it.isVarArgs }.matches(),
                resolveMethod().flatMap(methodName()).filter { it == "not" }.matches(),
            )

        val varArgExpressionParsing = methodCall().matches(isLogicalVarArg.matches())
            .zip(resolveMethod().flatMap(methodName()))
            .zip(
                allArguments()
                    .allMatching(requireNonNull<PsiElement>().map { it is PsiMethodCallExpression })
                    .mapMany(parseFilterExpression::invoke)
            ).map {
                val filter = it.first.first
                val methodName = it.first.second
                val filterNodes = it.second

                Node(
                    filter as PsiElement,
                    listOf(
                        Named(Name.from(methodName)),
                        HasFilter(
                            filterNodes,
                        ),
                    ),
                )
            }.acceptAnyError()

        val eqWithSingleArgument = methodCall()
            .filter { it.argumentList.expressionCount == 1 }.acceptAnyError()
            .matches(resolveMethod().flatMap(methodName()).filter { it == "eq" }.matches())
            .zip(argumentAt(0).flatMap(toValueReference()))
            .map {
                Node(
                    filter as PsiElement,
                    listOf(
                        Named(Name.EQ),
                        HasFieldReference(HasFieldReference.Known(it.first, "_id")),
                        it.second,
                    )
                )
            }.acceptAnyError()

        val twoParametersQueryBuilder = methodCall()
            .filter { it.argumentList.expressionCount == 2 }
            .zip(argumentAt(0).flatMap(toFieldReference()))
            .zip(argumentAt(1).flatMap(toValueReference()))
            .zip(methodCall().flatMap(resolveMethod()).flatMap(methodName()))
            .map {
                Node(
                    it.first.first.first as PsiElement,
                    listOf(
                        Named(Name.from(it.second)),
                        it.first.first.second,
                        it.first.second,
                    ),
                )
            }.acceptAnyError()

        parseFilterExpression.init(
            first(
                fixedArgumentsInOrNinParser,
                varargInOrNinParser,
                varArgExpressionParsing,
                eqWithSingleArgument,
                twoParametersQueryBuilder
            ).inputAs<PsiElement, _, _, _>()
                .mapAs<Node<PsiElement>, _, _, _>()
                .acceptAnyError()
        )

        return parseFilterExpression.asParser()
            .parseInReadAction(filter)
            .orElseNull()
    }

    private fun resolveToFiltersCall(element: PsiElement): PsiMethodCallExpression? {
        return resolveToCallToMongoDbQueryBuilder(FILTERS_FQN)
            .parseInReadAction(element)
            .orElseNull()
    }

    private fun resolveToUpdatesCall(element: PsiElement): PsiMethodCallExpression? {
        return resolveToCallToMongoDbQueryBuilder(UPDATES_FQN)
            .parseInReadAction(element)
            .orElseNull()
    }

    private fun resolveToCallToMongoDbQueryBuilder(builderFqn: String): Parser<PsiElement, Any, PsiMethodCallExpression> {
        val resolveToBuilder = lateInit<PsiElement, Any, PsiMethodCallExpression>()

        val isBuilderFunction = methodCall()
            .matches(
                resolveMethod()
                    .flatMap(containingClass())
                    .flatMap(classIs(builderFqn)).matches()
            ).inputAs<PsiElement, _, _, _>()

        val firstReturnStatementWithFilters = methodCall()
            .flatMap(resolveMethod())
            .flatMap(methodReturnStatements())
            .mapMany(resolveToBuilder::invoke)
            .firstResult()
            .mapAs<PsiMethodCallExpression, _, _, _>()
            .inputAs<PsiElement, _, _, _>()
            .acceptAnyError()

        val whenPsiMethodCallExpression = cond(
            isBuilderFunction.matches() to
                methodCall().inputAs<PsiElement, _, _, _>().acceptAnyError(),
            otherwiseParse(firstReturnStatementWithFilters)
        ).acceptAnyError()

        val whenIsVariable = variable()
            .flatMap(meaningfulExpression())
            .flatMap(resolveToBuilder::invoke)
            .mapAs<PsiMethodCallExpression, _, _, _>()
            .inputAs<PsiElement, _, _, _>()
            .acceptAnyError()

        val whenIsReferenceExpression = referenceExpression()
            .flatMap(meaningfulExpression())
            .flatMap(resolveToBuilder::invoke)
            .mapAs<PsiMethodCallExpression, _, _, _>()
            .inputAs<PsiElement, _, _, _>()
            .acceptAnyError()

        resolveToBuilder.init(
            first(
                whenPsiMethodCallExpression,
                whenIsVariable,
                whenIsReferenceExpression
            ).acceptAnyError()
        )

        return meaningfulExpression()
            .flatMap(resolveToBuilder::invoke)
            .acceptAnyError()
    }

    private fun parseUpdatesExpression(updateExpression: PsiMethodCallExpression): Node<PsiElement>? {
        val parseUpdatesExpression = lateInit<PsiElement, Any, Node<PsiElement>>()

        val updatesCombine = methodCall()
            .matches(
                resolveMethod().filter { it.isVarArgs }.flatMap(methodName()).filter {
                    it ==
                        "combine"
                }.matches()
            )
            .zip(resolveMethod().flatMap(methodName()))
            .zip(
                allArguments().mapMany(
                    resolveToCallToMongoDbQueryBuilder(UPDATES_FQN)
                ).mapMany(parseUpdatesExpression::invoke)
            )
            .map {
                Node(
                    it.first.first as PsiElement,
                    listOf(
                        Named(Name.from(it.first.second)),
                        HasUpdates(it.second)
                    )
                )
            }.acceptAnyError()

        val fieldValueUpdate = methodCall()
            .matches(allArguments().filter { it.size == 2 }.matches())
            .zip(resolveMethod().flatMap(methodName()))
            .zip(argumentAt(0).flatMap(toFieldReference()))
            .zip(argumentAt(1).flatMap(toValueReference()))
            .map {
                Node(
                    it.first.first.first as PsiElement,
                    listOf(
                        Named(Name.from(it.first.first.second)),
                        it.first.second,
                        it.second,
                    ),
                )
            }.acceptAnyError()

        val fieldOnlyUpdate = methodCall()
            .matches(allArguments().filter { it.size == 1 }.matches())
            .zip(resolveMethod().flatMap(methodName()))
            .zip(argumentAt(0).flatMap(toFieldReference()))
            .map {
                Node(
                    it.first.first as PsiElement,
                    listOf(
                        Named(Name.from(it.first.second)),
                        it.second
                    )
                )
            }.acceptAnyError()

        parseUpdatesExpression.init(
            first(
                updatesCombine,
                fieldValueUpdate,
                fieldOnlyUpdate
            ).inputAs<PsiElement, _, _, _>()
                .acceptAnyError()
        )

        return parseUpdatesExpression.asParser()
            .parseInReadAction(updateExpression)
            .orElseNull()
    }

    private fun hasMongoDbSessionReference(methodCall: PsiMethodCallExpression): Boolean {
        val hasEnoughArgs = methodCall.argumentList.expressionCount > 0
        if (!hasEnoughArgs) {
            return false
        }

        val typeOfFirstArg = methodCall.argumentList.expressionTypes[0]
        return typeOfFirstArg.equalsToText(SESSION_FQN)
    }

    private fun methodCallToCommand(methodCall: PsiMethodCallExpression?): IsCommand {
        val resolveFromMethodName = methodCall()
            .flatMap(resolveMethod())
            .flatMap(methodName())
            .flatMap(
                cond(
                    equalsTo("countDocuments") to constant(IsCommand.CommandType.COUNT_DOCUMENTS),
                    equalsTo("estimatedDocumentCount") to
                        constant(IsCommand.CommandType.ESTIMATED_DOCUMENT_COUNT),
                    equalsTo("distinct") to constant(IsCommand.CommandType.DISTINCT),
                    equalsTo("find") to constant(IsCommand.CommandType.FIND_MANY),
                    equalsTo("first") to constant(IsCommand.CommandType.FIND_ONE),
                    equalsTo("aggregate") to constant(IsCommand.CommandType.AGGREGATE),
                    equalsTo("insertOne") to constant(IsCommand.CommandType.INSERT_ONE),
                    equalsTo("insertMany") to constant(IsCommand.CommandType.INSERT_MANY),
                    equalsTo("deleteOne") to constant(IsCommand.CommandType.DELETE_ONE),
                    equalsTo("deleteMany") to constant(IsCommand.CommandType.DELETE_MANY),
                    equalsTo("replaceOne") to constant(IsCommand.CommandType.REPLACE_ONE),
                    equalsTo("updateOne") to constant(IsCommand.CommandType.UPDATE_ONE),
                    equalsTo("updateMany") to constant(IsCommand.CommandType.UPDATE_MANY),
                    equalsTo("findOneAndDelete") to
                        constant(IsCommand.CommandType.FIND_ONE_AND_DELETE),
                    equalsTo("findOneAndReplace") to
                        constant(IsCommand.CommandType.FIND_ONE_AND_REPLACE),
                    equalsTo("findOneAndUpdate") to
                        constant(IsCommand.CommandType.FIND_ONE_AND_UPDATE)
                )
            ).acceptAnyError()

        val onIterableResolveUpwards = methodCall()
            .matches(
                resolveMethod()
                    .flatMap(containingClass())
                    .flatMap(classIs(ITERABLE_FQN))
                    .matches()
            )
            .flatMap(methodCallChain()).acceptAnyError()
            .firstMatching(
                resolveFromMethodName.flatMap(equalsTo(IsCommand.CommandType.AGGREGATE)).matches()
            ).flatMap(resolveFromMethodName)
            .acceptAnyError()

        return first(
            onIterableResolveUpwards,
            resolveFromMethodName,
        ).parseInReadAction(methodCall)
            .map(::IsCommand)
            .orElse { IsCommand(IsCommand.CommandType.UNKNOWN) }
    }
}

fun <I, E, O> Parser<I, E, O>.parseInReadAction(input: I): Either<E, O> {
    return runBlocking {
        readAction {
            runBlocking {
                this@parseInReadAction(input)
            }
        }
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
