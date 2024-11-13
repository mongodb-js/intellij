package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.parentOfType
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.allArguments
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.argumentAt
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.classIs
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.containingClass
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.isArray
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.isJavaIterable
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.isMethodFromDriverMongoDbCollection
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.meaningfulExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.methodCall
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.methodCallChain
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.methodName
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.methodReturnStatements
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.referenceExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.resolveMethod
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.resolveType
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.toFieldReference
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.toValueFromArgumentList
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.toValueReference
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.typeToClass
import com.mongodb.jbplugin.dialects.javadriver.glossary.parser.variable
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.HasUpdates
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import com.mongodb.jbplugin.mql.parser.Parser
import com.mongodb.jbplugin.mql.parser.acceptAnyError
import com.mongodb.jbplugin.mql.parser.allMatching
import com.mongodb.jbplugin.mql.parser.cond
import com.mongodb.jbplugin.mql.parser.constant
import com.mongodb.jbplugin.mql.parser.debug
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
import com.mongodb.jbplugin.mql.parser.zip
import kotlinx.coroutines.runBlocking

private const val ITERABLE_FQN = "com.mongodb.client.MongoIterable"
private const val SESSION_FQN = "com.mongodb.client.ClientSession"
private const val FILTERS_FQN = "com.mongodb.client.model.Filters"
private const val UPDATES_FQN = "com.mongodb.client.model.Updates"

object JavaDriverDialectParser : DialectParser<PsiElement> {
    override fun isCandidateForQuery(source: PsiElement) =
        methodCall()
            .flatMap(methodCallToCommand())
            .filter { it.type != IsCommand.CommandType.UNKNOWN }
            .map { true }
            .parseInReadAction(source)
            .orElse { false }

    override fun attachment(source: PsiElement): PsiElement = source.findTopParentBy {
        isCandidateForQuery(it)
    }!!

    override fun parse(source: PsiElement): Node<PsiElement> {
        val parse = lateInit<PsiElement, Any, Node<PsiElement>>()

        val sourceDialect = HasSourceDialect(HasSourceDialect.DialectName.JAVA_DRIVER)
        val collectionReference = NamespaceExtractor.extractNamespace(source)

        val isAMongoDbCollectionMethod = queryRoot()
            .flatMap(methodCall())
            .acceptAnyError()
            .debug("1")
            .matches(resolveMethod().flatMap(isMethodFromDriverMongoDbCollection()).matches())
            .debug("2")
            .zip(
                first(
                    parseAllFiltersFromCurrentCall(),
                    constant(HasFilter<PsiElement>(emptyList()))
                )
            )
            .debug("3")
            .zip(
                first(
                    parseAllUpdatesFromCurrentCall(),
                    constant(HasUpdates<PsiElement>(emptyList()))
                )
            )
            .debug("4")
            .zip(first(methodCallToCommand(), constant(IsCommand(IsCommand.CommandType.UNKNOWN))))
            .map {
                Node(
                    it.first.first.first as PsiElement,
                    listOf(
                        HasSourceDialect(HasSourceDialect.DialectName.JAVA_DRIVER),
                        collectionReference,
                        it.first.first.second,
                        it.first.second,
                        it.second,
                    )
                )
            }.acceptAnyError()
            .inputAs<PsiElement, _, _, _>()

        val resolveUnderlyingQuery = queryRoot()
            .flatMap(resolveMethod())
            .flatMap(methodReturnStatements())
            .mapMany(parse::invoke)
            .firstResult()
            .acceptAnyError()
            .inputAs<PsiElement, _, _, _>()

        val emptyQuery = queryRoot()
            .zip(methodCallToCommand())
            .map {
                Node(
                    it.first as PsiElement,
                    listOf(
                        sourceDialect,
                        collectionReference,
                        it.second
                    )
                )
            }.acceptAnyError()
            .inputAs<PsiElement, _, _, _>()

        parse.init(
            first(
                isAMongoDbCollectionMethod,
                resolveUnderlyingQuery,
                emptyQuery
            ).acceptAnyError()
        )

        return parse.asParser().parseInReadAction(source).orElseNull()!!
    }

    private fun parseAllFiltersFromCurrentCall(): Parser<PsiMethodCallExpression, Any, HasFilter<PsiElement>> {
        val whenHasSessionArgument = methodCall().matches(hasMongoDbSessionReference())
            .flatMap(argumentAt(1))
            .flatMap(resolveToCallToMongoDbQueryBuilder(FILTERS_FQN))
            .flatMap(parseFilterExpression())
            .acceptAnyError()

        val whenDoesNotHaveSessionArgument = methodCall()
            .flatMap(argumentAt(0))
            .flatMap(resolveToCallToMongoDbQueryBuilder(FILTERS_FQN))
            .flatMap(parseFilterExpression())
            .acceptAnyError()

        return first(
            whenHasSessionArgument,
            whenDoesNotHaveSessionArgument
        ).acceptAnyError()
            .map { HasFilter(listOf(it)) }
    }

    private fun parseAllUpdatesFromCurrentCall(): Parser<PsiMethodCallExpression, Any, HasUpdates<PsiElement>> {
        val whenHasSessionArgument = methodCall().matches(hasMongoDbSessionReference())
            .flatMap(argumentAt(2)) // the update is the 3rd argument
            .flatMap(resolveToCallToMongoDbQueryBuilder(UPDATES_FQN))
            .flatMap(parseUpdatesExpression())
            .acceptAnyError()

        val whenDoesNotHaveSessionArgument = methodCall()
            .flatMap(argumentAt(1)) // the update is the 2nd argument
            .flatMap(resolveToCallToMongoDbQueryBuilder(UPDATES_FQN))
            .flatMap(parseUpdatesExpression())
            .acceptAnyError()

        return first(
            whenHasSessionArgument,
            whenDoesNotHaveSessionArgument
        ).acceptAnyError().map { HasUpdates(listOf(it)) }
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

    private fun parseFilterExpression(): Parser<PsiMethodCallExpression, Any, Node<PsiElement>> {
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
                    .allMatching(methodCall().matches())
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
                    it.first as PsiElement,
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

    private fun parseUpdatesExpression(): Parser<PsiMethodCallExpression, Any, Node<PsiElement>> {
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
    }

    private fun hasMongoDbSessionReference(): Parser<PsiMethodCallExpression, Any, Boolean> {
        return methodCall().matches(
            argumentAt(
                0
            ).flatMap(resolveType()).flatMap(typeToClass()).flatMap(classIs(SESSION_FQN)).matches()
        ).map { true }
            .acceptAnyError()
    }

    private fun methodCallToCommand(): Parser<PsiMethodCallExpression, Any, IsCommand> {
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
        ).map(::IsCommand).acceptAnyError()
    }

    private fun queryRoot(): Parser<PsiElement, Any, PsiMethodCallExpression> {
        return methodCall()
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
