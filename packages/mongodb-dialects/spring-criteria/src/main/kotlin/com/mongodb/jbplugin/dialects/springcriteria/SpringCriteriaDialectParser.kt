package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.parentOfType
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.dialects.javadriver.glossary.*
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.*
import com.mongodb.jbplugin.mql.toBsonType

private const val CRITERIA_CLASS_FQN = "org.springframework.data.mongodb.core.query.Criteria"
private const val DOCUMENT_FQN = "org.springframework.data.mongodb.core.mapping.Document"

object SpringCriteriaDialectParser : DialectParser<PsiElement> {
    override fun isCandidateForQuery(source: PsiElement) =
        source.findCriteriaWhereExpression() != null

    override fun attachment(source: PsiElement): PsiElement = source.findCriteriaWhereExpression()!!

    override fun parse(source: PsiElement): Node<PsiElement> {
        if (source !is PsiExpression) {
            return Node(source, emptyList())
        }

        val criteriaChain = source.findCriteriaWhereExpression() ?: return Node(source, emptyList())
        val targetCollection = QueryTargetCollectionExtractor.extractCollection(source)

        val mongoOpCall = criteriaChain.parentMongoDbOperation() ?: return Node(source, emptyList())
        val mongoOpMethod =
            mongoOpCall.fuzzyResolveMethod() ?: return Node(mongoOpCall, emptyList())

        val command = inferCommandFromMethod(mongoOpMethod)

        // not all methods work the same way (sigh) so we will need a big `when` to handle
        // each special case
        return when (mongoOpMethod.name) {
            "all",
            "one" -> {
                // these are terminal operators, so the query is just above us (below in the PSI)
                val actualMethod = mongoOpCall.firstChild?.firstChild as? PsiMethodCallExpression
                    ?: return Node(mongoOpCall, listOf(command, targetCollection))

                return Node(
                    actualMethod,
                    listOf(
                        command,
                        targetCollection,
                        HasFilter(
                            parseFilterRecursively(
                                actualMethod.argumentList.expressions.getOrNull(0)
                            )
                                .reversed()
                        )
                    )
                )
            }

            "count",
            "exactCount",
            "exists",
            "find",
            "findAll",
            "findAllAndRemove",
            "findAndRemove",
            "findOne",
            "scroll",
            "stream" -> Node(
                mongoOpMethod,
                listOf(
                    command,
                    targetCollection,
                    HasFilter(
                        parseFilterRecursively(mongoOpCall.argumentList.expressions.getOrNull(0))
                            .reversed()
                    )
                )
            )
            "findAndModify",
            "findAndReplace",
            "updateFirst",
            "updateMulti",
            "upsert" -> Node(
                mongoOpMethod,
                listOf(
                    command,
                    targetCollection,
                    HasFilter(
                        parseFilterRecursively(mongoOpCall.argumentList.expressions.getOrNull(0))
                            .reversed()
                    )
                )
            )
            "findById" -> Node(
                mongoOpMethod,
                listOf(
                    command,
                    targetCollection,
                )
            )
            "findDistinct" -> Node(
                mongoOpMethod,
                listOf(
                    command,
                    targetCollection,
                    HasFilter(
                        parseFilterRecursively(mongoOpCall.argumentList.expressions.getOrNull(0))
                            .reversed()
                    )
                )
            )
            "insert" -> Node(
                mongoOpMethod,
                listOf(
                    command,
                    targetCollection,
                )
            )
            "insertAll" -> Node(
                mongoOpMethod,
                listOf(
                    command,
                    targetCollection,
                )
            )
            "remove" -> Node(
                mongoOpMethod,
                listOf(
                    command,
                    targetCollection,
                    HasFilter(
                        parseFilterRecursively(mongoOpCall.argumentList.expressions.getOrNull(0))
                            .reversed()
                    )
                )
            )
            "replace" -> Node(
                mongoOpMethod,
                listOf(
                    command,
                    targetCollection,
                    HasFilter(
                        parseFilterRecursively(mongoOpCall.argumentList.expressions.getOrNull(0))
                            .reversed()
                    )
                )
            )
            else -> Node(
                mongoOpCall,
                listOf(
                    command,
                    targetCollection,
                    HasFilter(
                        parseFilterRecursively(mongoOpCall.argumentList.expressions.getOrNull(0))
                    )
                )
            )
        }
    }

    override fun isReferenceToDatabase(source: PsiElement): Boolean {
        return false // databases are in property files and we don't support AC there yet
    }

    override fun isReferenceToCollection(source: PsiElement): Boolean {
        val docAnnotation = source.parentOfType<PsiAnnotation>() ?: return false
        return docAnnotation.hasQualifiedName(DOCUMENT_FQN)
    }

    override fun isReferenceToField(source: PsiElement): Boolean {
        val isString =
            source.parentOfType<PsiLiteralExpression>(true)?.tryToResolveAsConstantString() != null
        val methodCall = source.parentOfType<PsiMethodCallExpression>() ?: return false

        /*
         * IntelliJ might detect that we are not in a string, but in a whitespace or a dot due to, probably,
         * some internal race conditions. In this case, we will check the parent, which will be an ExpressionList, that
         * will contain all tokens and the string we actually want. In case it's a dot, we are here:
         * where(). <--
         * So we need to check the previous sibling to find if we are in a criteria expression.
         */
        if (source is PsiWhiteSpace ||
            (source is PsiJavaToken && source.elementType?.toString() != "STRING_LITERAL")
        ) {
            val parentExpressionList = source.parent
            val siblingAsMethodCall = source.prevSibling as? PsiMethodCallExpression ?: return false

            return siblingAsMethodCall.isCriteriaExpression() ||
                parentExpressionList.children.filterIsInstance<PsiExpression>().any {
                    isReferenceToField(it)
                }
        }

        return isString && methodCall.isCriteriaExpression()
    }

    private fun parseFilterRecursively(
        valueFilterExpression: PsiElement?
    ): List<Node<PsiElement>> {
        if (valueFilterExpression == null) {
            return emptyList()
        }

        val valueMethodCall =
            valueFilterExpression.meaningfulExpression() as? PsiMethodCallExpression
                ?: return emptyList()

        val valueFilterMethod = valueMethodCall.fuzzyResolveMethod() ?: return emptyList()

        // 1st scenario: vararg operations
        // for example, andOperator/orOperator...
        if (valueFilterMethod.isVarArgs) {
            val childrenNodes = valueMethodCall.argumentList.expressions.flatMap {
                parseFilterRecursively(it).reversed()
            }

            val thisQueryNode = listOf(
                Node(
                    valueFilterExpression,
                    listOf(
                        operatorName(valueFilterMethod),
                        HasFilter(childrenNodes)
                    )
                )
            )

            // we finished parsing the vararg operator, check the tail of the query to see if there are more
            // filters.
            //                     v------------------- we want to see if there is something here
            //                                 v------- valueMethodCall is here
            // $nextFieldRef$.$nextValueRef$.$varargOp$("abc")
            val nextQueryExpression = valueMethodCall.firstChild?.firstChild
            if (nextQueryExpression != null && nextQueryExpression is PsiMethodCallExpression) {
                return thisQueryNode + parseFilterRecursively(nextQueryExpression)
            }

            return thisQueryNode
        }

        //                   v----------------------- field filter (it can be a where, an and...)
        //                             v------------- valueMethodCall
        //                                     v----- the value itself
        // 2st scenario: $fieldRef$.$filter$("abc")
        val fieldMethodCall =
            valueMethodCall.firstChild.firstChild.meaningfulExpression() as? PsiMethodCallExpression
                ?: return emptyList()
        val valuePsi = valueMethodCall.argumentList.expressions.getOrNull(0)
        val fieldPsi = fieldMethodCall.argumentList.expressions.getOrNull(0)

        val field = fieldPsi?.tryToResolveAsConstantString()
        val fieldReference = when (field) {
            null -> HasFieldReference(HasFieldReference.Unknown)
            else -> HasFieldReference(HasFieldReference.Known(fieldPsi, field))
        }
        val (_, value) = valuePsi?.tryToResolveAsConstant() ?: (false to null)
        val valueReference = when (value) {
            null -> when (valuePsi?.type) {
                null -> HasValueReference(HasValueReference.Unknown)
                else -> HasValueReference(
                    HasValueReference.Runtime(
                        valuePsi,
                        valuePsi.type?.toBsonType() ?: BsonAny
                    )
                )
            }
            else -> HasValueReference(
                HasValueReference.Constant(valuePsi, value, value.javaClass.toBsonType(value))
            )
        }

        val operationName = operatorName(valueFilterMethod)

        // we finished parsing the first filter, check the tail of the query to see if there are more
        // filters.
        //                     v------------------- we want to see if there is something here
        //                                 v------- fieldMethodCall is here
        // $nextFieldRef$.$nextValueRef$.$fieldRef$.$filter$("abc")
        val nextQueryExpression = fieldMethodCall.firstChild?.firstChild
        val thisQueryNode = listOf(
            Node(
                valueFilterExpression,
                listOf(
                    operationName,
                    fieldReference,
                    valueReference
                )
            )
        )

        if (nextQueryExpression != null && nextQueryExpression is PsiMethodCallExpression) {
            return thisQueryNode + parseFilterRecursively(nextQueryExpression)
        }

        return thisQueryNode
    }

    private fun operatorName(currentCriteriaMethod: PsiMethod): Named {
        val name = currentCriteriaMethod.name.replace("Operator", "")
        val named = Named(name.toName())
        return named
    }

    /**
     * List of methods from here:
     * https://docs.spring.io/spring-data/mongodb/docs/current/api/org/springframework/data/mongodb/core/MongoOperations.html
     */
    private fun inferCommandFromMethod(mongoOpMethod: PsiMethod): IsCommand {
        return IsCommand(
            when (mongoOpMethod.name) {
                "aggregate", "aggregateStream" -> IsCommand.CommandType.AGGREGATE
                "count", "exactCount" -> IsCommand.CommandType.COUNT_DOCUMENTS
                "estimatedCount" -> IsCommand.CommandType.ESTIMATED_DOCUMENT_COUNT
                "exists" -> IsCommand.CommandType.FIND_ONE
                "find", "findAll" -> IsCommand.CommandType.FIND_MANY
                "findDistinct" -> IsCommand.CommandType.DISTINCT
                "findAllAndRemove" -> IsCommand.CommandType.DELETE_MANY
                "findAndModify" -> IsCommand.CommandType.FIND_ONE_AND_UPDATE
                "findAndRemove" -> IsCommand.CommandType.FIND_ONE_AND_DELETE
                "findAndReplace" -> IsCommand.CommandType.FIND_ONE_AND_REPLACE
                "findById" -> IsCommand.CommandType.FIND_ONE
                "insert" -> IsCommand.CommandType.INSERT_ONE
                "insertAll" -> IsCommand.CommandType.INSERT_MANY
                "remove" -> IsCommand.CommandType.DELETE_MANY
                "replace" -> IsCommand.CommandType.REPLACE_ONE
                "save" -> IsCommand.CommandType.UPSERT
                "scroll", "stream" -> IsCommand.CommandType.FIND_MANY
                "updateFirst" -> IsCommand.CommandType.UPDATE_ONE
                "updateMulti" -> IsCommand.CommandType.UPDATE_MANY
                "upsert" -> IsCommand.CommandType.UPSERT
                "one", "oneValue", "first", "firstValue" -> IsCommand.CommandType.FIND_ONE
                "all" -> IsCommand.CommandType.FIND_MANY
                else -> IsCommand.CommandType.UNKNOWN
            }
        )
    }
}

/**
 * Returns whether the current method is a criteria method.
 *
 * @return
 */
fun PsiMethodCallExpression.isCriteriaExpression(): Boolean {
    val method = fuzzyResolveMethod() ?: return false
    return method.containingClass?.qualifiedName == CRITERIA_CLASS_FQN
}

private fun PsiElement.findCriteriaWhereExpression(): PsiMethodCallExpression? {
    val parentStatement = PsiTreeUtil.getParentOfType(this, PsiStatement::class.java) ?: return null
    val methodCalls = parentStatement.findAllChildrenOfType(PsiMethodCallExpression::class.java)
    var bottomLevel: PsiMethodCallExpression = methodCalls.find { methodCall ->
        val method = methodCall.fuzzyResolveMethod() ?: return@find false
        method.containingClass?.qualifiedName == CRITERIA_CLASS_FQN &&
            method.name == "where"
    } ?: return null

    while (bottomLevel.text.startsWith("where")) {
        bottomLevel = (bottomLevel.parent as? PsiMethodCallExpression) ?: return bottomLevel
    }

    return bottomLevel
}

private fun PsiMethodCallExpression.isCriteriaQueryMethod(): Boolean {
    val method = fuzzyResolveMethod() ?: return false
    return method.containingClass?.qualifiedName == CRITERIA_CLASS_FQN
}

private fun PsiMethodCallExpression.parentMongoDbOperation(): PsiMethodCallExpression? {
    var parentMethodCall = findParentOfType<PsiMethodCallExpression>() ?: return null
    val method = parentMethodCall.fuzzyResolveMethod() ?: return null

    if (INTERFACES_WITH_QUERY_METHODS.any {
            method.containingClass?.qualifiedName?.contains(it) ==
                true
        }
    ) {
        return parentMethodCall.parentMongoDbOperation() ?: parentMethodCall
    }

    return parentMethodCall.parentMongoDbOperation()
}

private fun String.toName(): Name = when (this) {
    "is" -> Name.EQ
    else -> Name.from(this)
}

/**
 * As MongoOperations <b>implement a ton</b> of interfaces, we need to check in which one
 * we've found the method:
 *
 * https://docs.spring.io/spring-data/mongodb/docs/current/api/org/springframework/data/mongodb/core/MongoOperations.html
 *
 * We are not going to support mapReduce as they are deprecated in MongoDB.
 */
private val INTERFACES_WITH_QUERY_METHODS = arrayOf(
    "MongoTemplate",
    "MongoOperations",
    "ExecutableAggregationOperation",
    "ExecutableFindOperation",
    "ExecutableInsertOperation",
    "ExecutableMapReduceOperation",
    "ExecutableRemoveOperation",
    "ExecutableUpdateOperation",
    "FluentMongoOperations",
    "ExecutableAggregationOperation",
    "ExecutableFindOperation",
    "ExecutableInsertOperation",
    "ExecutableRemoveOperation",
    "ExecutableUpdateOperation",
)
