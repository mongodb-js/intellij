package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.OutputQuery
import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.mql.*
import com.mongodb.jbplugin.mql.components.*
import com.mongodb.jbplugin.mql.components.HasFieldReference.Computed
import com.mongodb.jbplugin.mql.components.HasFieldReference.FromSchema
import com.mongodb.jbplugin.mql.components.HasFieldReference.Unknown
import com.mongodb.jbplugin.mql.parser.anyError
import com.mongodb.jbplugin.mql.parser.components.aggregationStages
import com.mongodb.jbplugin.mql.parser.components.allFiltersRecursively
import com.mongodb.jbplugin.mql.parser.components.hasName
import com.mongodb.jbplugin.mql.parser.components.whenIsCommand
import com.mongodb.jbplugin.mql.parser.count
import com.mongodb.jbplugin.mql.parser.filter
import com.mongodb.jbplugin.mql.parser.map
import com.mongodb.jbplugin.mql.parser.matches
import com.mongodb.jbplugin.mql.parser.nth
import com.mongodb.jbplugin.mql.parser.parse
import io.github.z4kn4fein.semver.Version
import org.owasp.encoder.Encode

object MongoshDialectFormatter : DialectFormatter {
    override fun <S> formatQuery(
        query: Node<S>,
        explain: Boolean,
    ): OutputQuery {
        val isAggregate = isAggregate(query)
        val canEmitAggregate = canEmitAggregate(query)

        val outputString = MongoshBackend(prettyPrint = explain).apply {
            if (isAggregate && !canEmitAggregate) {
                emitComment("Only aggregates with a single match stage can be converted.")
                return@apply
            }

            emitDbAccess()
            emitCollectionReference(query.component<HasCollectionReference<S>>())
            if (explain) {
                emitFunctionName("explain")
                emitFunctionCall()
                emitPropertyAccess()
            }
            emitFunctionName(query.component<IsCommand>()?.type?.canonical ?: "find")
            emitFunctionCall(long = true, {
                if (isAggregate(query)) {
                    emitAggregateBody(query)
                } else {
                    emitQueryBody(query, firstCall = true)
                }
            })
        }.computeOutput()

        val ref = query.component<HasCollectionReference<S>>()?.reference
        return when {
            isAggregate && !canEmitAggregate -> OutputQuery.Incomplete(outputString)
            ref is HasCollectionReference.Known -> if (ref.namespace.isValid) {
                OutputQuery.CanBeRun(outputString)
            } else {
                OutputQuery.Incomplete(outputString)
            }
            else -> OutputQuery.Incomplete(outputString)
        }
    }

    override fun <S> indexCommandForQuery(query: Node<S>): String = when (
        val index = IndexAnalyzer.analyze(
            query
        )
    ) {
        is IndexAnalyzer.SuggestedIndex.NoIndex -> ""
        is IndexAnalyzer.SuggestedIndex.MongoDbIndex -> {
            val targetCluster = query.component<HasTargetCluster>()
            val version = targetCluster?.majorVersion ?: Version(7)
            val docPrefix = "https://www.mongodb.com/docs/v${version.major}.${version.minor}"

            val fieldList = index.fields.joinToString { Encode.forJavaScript(it.fieldName) }
            val (dbName, collName) = when (val collRef = index.collectionReference.reference) {
                is HasCollectionReference.Unknown -> ("<database>" to "<collection>")
                is HasCollectionReference.OnlyCollection -> ("<database>" to collRef.collection)
                is HasCollectionReference.Known -> (
                    collRef.namespace.database to
                        collRef.namespace.collection
                    )
            }

            val encodedDbName = Encode.forJavaScript(dbName)
            val encodedColl = Encode.forJavaScript(collName)

            val indexTemplate = index.fields.withIndex().joinToString(
                separator = ", ",
                prefix = "{ ",
                postfix = " }"
            ) {
                """ "<your_field_${it.index + 1}>": 1 """.trim()
            }

            """
                    // Potential fields to consider indexing: $fieldList
                    // Learn about creating an index: $docPrefix/core/data-model-operations/#indexes
                    db.getSiblingDB("$encodedDbName").getCollection("$encodedColl")
                      .createIndex($indexTemplate)
            """.trimIndent()
        }
    }

    override fun formatType(type: BsonType) = ""
}

private fun <S> MongoshBackend.emitQueryBody(
    node: Node<S>,
    firstCall: Boolean = false
): MongoshBackend {
    val named = node.component<Named>()
    val fieldRef = node.component<HasFieldReference<S>>()
    val valueRef = node.component<HasValueReference<S>>()
    val hasFilter = node.component<HasFilter<S>>()
    val isLong = allFiltersRecursively<S>().parse(node).orElse { emptyList() }.size > 3

    if (hasFilter != null && fieldRef == null && valueRef == null && named == null) {
        // 1. has children, nothing else (root node)
        if (firstCall) {
            emitObjectStart(long = isLong)
        }

        hasFilter.children.forEach {
            emitQueryBody(it)
            emitObjectValueEnd()
        }
        if (firstCall) {
            emitObjectEnd(long = isLong)
        }
    } else if (hasFilter == null && fieldRef != null && valueRef != null && named == null) {
        // 2. no children, only a field: value case
        if (firstCall) {
            emitObjectStart(long = isLong)
        }
        emitObjectKey(resolveFieldReference(fieldRef))
        emitContextValue(resolveValueReference(valueRef, fieldRef))
        if (firstCall) {
            emitObjectEnd(long = isLong)
        }
    } else {
        named?.let {
// 3. children and named
            if (named.name == Name.EQ) {
// normal a: b case
                if (firstCall) {
                    emitObjectStart(long = isLong)
                }
                if (fieldRef != null) {
                    emitObjectKey(resolveFieldReference(fieldRef))
                }

                if (valueRef != null) {
                    emitContextValue(resolveValueReference(valueRef, fieldRef))
                }

                hasFilter?.children?.forEach {
                    emitQueryBody(it)
                    emitObjectValueEnd()
                }

                if (firstCall) {
                    emitObjectEnd(long = isLong)
                }
            } else if (setOf( // 1st basic attempt, to improve in INTELLIJ-76
                    Name.GT,
                    Name.GTE,
                    Name.LT,
                    Name.LTE
                ).contains(named.name) &&
                valueRef != null
            ) {
// a: { $gt: 1 }
                if (firstCall) {
                    emitObjectStart(long = isLong)
                }

                if (fieldRef != null) {
                    emitObjectKey(resolveFieldReference(fieldRef))
                }

                emitObjectStart()
                emitObjectKey(registerConstant('$' + named.name.canonical))
                emitContextValue(resolveValueReference(valueRef, fieldRef))
                emitObjectEnd()

                if (firstCall) {
                    emitObjectEnd(long = isLong)
                }
            } else if (setOf(
                    // 1st basic attempt, to improve in INTELLIJ-77
                    Name.AND,
                    Name.OR,
                    Name.NOR,
                ).contains(named.name)
            ) {
                if (firstCall) {
                    emitObjectStart()
                }
                emitObjectKey(registerConstant('$' + named.name.canonical))
                emitArrayStart(long = true)
                hasFilter?.children?.forEach {
                    emitObjectStart()
                    emitQueryBody(it)
                    emitObjectEnd()
                    emitObjectValueEnd()
                    if (prettyPrint) {
                        emitNewLine()
                    }
                }
                emitArrayEnd(long = true)
                if (firstCall) {
                    emitObjectEnd()
                }
            } else if (named.name == Name.NOT && hasFilter?.children?.size == 1) {
                // the not operator is a special case
                // because we receive it as:
                // $not: { $field$: $condition$ }
                // and it needs to be:
                // $field$: { $not: $condition$ }
                // we will do a JIT translation

                var innerChild = hasFilter.children.first()
                val operation = innerChild.component<Named>()
                val fieldRef = innerChild.component<HasFieldReference<S>>()
                val valueRef = innerChild.component<HasValueReference<S>>()

                if (fieldRef == null) { // we are in an "and" / "or"...
                    // so we use $nor instead
                    emitQueryBody(
                        Node(
                            node.source,
                            node.components<Component>().filterNot { it is Named } + Named(Name.NOR)
                        )
                    )
                    return@let
                }

                if (operation == null && valueRef == null) {
                    return@let
                }

                if (firstCall) {
                    emitObjectStart()
                }

                // emit field name first
                emitObjectKey(resolveFieldReference(fieldRef))
                // emit the $not
                emitObjectStart()
                emitObjectKey(registerConstant('$' + "not"))
                emitQueryBody(
                    Node(
                        innerChild.source,
                        listOfNotNull(
                            operation,
                            valueRef
                        )
                    )
                )
                emitObjectEnd()

                if (firstCall) {
                    emitObjectEnd()
                }
            } else if (named.name != Name.UNKNOWN && fieldRef != null && valueRef != null) {
                if (firstCall) {
                    emitObjectStart(long = isLong)
                }
                emitObjectKey(resolveFieldReference(fieldRef))
                emitObjectStart(long = isLong)
                emitObjectKey(registerConstant('$' + named.name.canonical))
                emitContextValue(resolveValueReference(valueRef, fieldRef))
                emitObjectEnd(long = isLong)
                if (firstCall) {
                    emitObjectEnd(long = isLong)
                }
            }
        }
    }

    return this
}

private fun <S> MongoshBackend.emitAggregateBody(node: Node<S>): MongoshBackend {
    // here we can assume that we only have 1 single stage that is a match
    val matchStage = node.component<HasAggregation<S>>()!!.children[0]
    val filter = matchStage.component<HasFilter<S>>()?.children?.getOrNull(0)
    val longFilter = filter?.component<HasFilter<S>>()?.children?.size?.let { it > 3 } == true

    emitArrayStart(long = true)
    emitObjectStart()
    emitObjectKey(registerConstant('$' + "match"))
    if (filter != null) {
        emitObjectStart(long = longFilter)
        emitQueryBody(filter)
        emitObjectEnd(long = longFilter)
    } else {
        emitComment("No filter provided.")
    }
    emitObjectEnd()
    emitArrayEnd(long = true)

    return this
}

private fun <S> isAggregate(node: Node<S>): Boolean {
    return whenIsCommand<S>(IsCommand.CommandType.AGGREGATE)
        .map { true }
        .parse(node).orElse { false }
}

private fun <S> canEmitAggregate(node: Node<S>): Boolean {
    return aggregationStages<S>()
        .matches(count<Node<S>>().filter { it >= 1 }.matches().anyError())
        .nth(0)
        .matches(hasName(Name.MATCH))
        .map { true }
        .parse(node).orElse { false }
}

private fun <S> MongoshBackend.resolveValueReference(
    valueRef: HasValueReference<S>,
    fieldRef: HasFieldReference<S>?
) = when (val ref = valueRef.reference) {
    is HasValueReference.Constant -> registerConstant(ref.value)
    is HasValueReference.Runtime -> registerVariable(
        (fieldRef?.reference as? FromSchema)?.fieldName ?: "value",
        ref.type
    )

    else -> registerVariable(
        "queryField",
        BsonAny
    )
}

private fun <S> MongoshBackend.resolveFieldReference(fieldRef: HasFieldReference<S>) =
    when (val ref = fieldRef.reference) {
        is Computed -> registerConstant(ref.fieldName)
        is FromSchema -> registerConstant(ref.fieldName)
        is Unknown -> registerVariable("field", BsonAny)
    }

private fun <S> MongoshBackend.emitCollectionReference(collRef: HasCollectionReference<S>?): MongoshBackend {
    when (val ref = collRef?.reference) {
        is HasCollectionReference.OnlyCollection -> {
            emitDatabaseAccess(registerVariable("database", BsonString))
            emitCollectionAccess(registerConstant(ref.collection))
        }

        is HasCollectionReference.Known -> {
            emitDatabaseAccess(registerConstant(ref.namespace.database))
            emitCollectionAccess(registerConstant(ref.namespace.collection))
        }

        else -> {
            emitDatabaseAccess(registerVariable("database", BsonString))
            emitCollectionAccess(registerVariable("collection", BsonString))
        }
    }

    return this
}
