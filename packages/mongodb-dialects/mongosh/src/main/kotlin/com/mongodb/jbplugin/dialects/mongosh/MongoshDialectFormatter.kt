package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.OutputQuery
import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.mql.*
import com.mongodb.jbplugin.mql.components.*
import io.github.z4kn4fein.semver.Version
import org.owasp.encoder.Encode

object MongoshDialectFormatter : DialectFormatter {
    override fun <S> formatQuery(query: Node<S>, explain: Boolean): OutputQuery {
        val outputString = MongoshBackend().apply {
            emitDbAccess()
            emitCollectionReference(query.component<HasCollectionReference<S>>())
            emitFunctionName("find")
            emitFunctionCall({
                emitQueryBody(query, firstCall = true)
            })
            if (explain) {
                emitPropertyAccess()
                emitFunctionName("explain")
                emitFunctionCall()
            }
        }.computeOutput()

        return when (val ref = query.component<HasCollectionReference<S>>()?.reference) {
            is HasCollectionReference.Known -> if (ref.namespace.isValid) {
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
    val hasChildren = node.component<HasChildren<S>>()

    if (hasChildren != null && fieldRef == null && valueRef == null && named == null) {
        // 1. has children, nothing else (root node)
        if (firstCall) {
            emitObjectStart()
        }

        hasChildren.children.forEach {
            emitQueryBody(it)
            emitObjectValueEnd()
        }
        if (firstCall) {
            emitObjectEnd()
        }
    } else if (hasChildren == null && fieldRef != null && valueRef != null && named == null) {
        // 2. no children, only a field: value case
        if (firstCall) {
            emitObjectStart()
        }
        emitObjectKey(resolveFieldReference(fieldRef))
        emitContextValue(resolveValueReference(valueRef, fieldRef))
        if (firstCall) {
            emitObjectEnd()
        }
    } else {
        named?.let {
// 3. children and named
            if (named.name == Name.EQ) {
// normal a: b case
                if (firstCall) {
                    emitObjectStart()
                }
                if (fieldRef != null) {
                    emitObjectKey(resolveFieldReference(fieldRef))
                }

                if (valueRef != null) {
                    emitContextValue(resolveValueReference(valueRef, fieldRef))
                }

                hasChildren?.children?.forEach {
                    emitQueryBody(it)
                    emitObjectValueEnd()
                }

                if (firstCall) {
                    emitObjectEnd()
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
                    emitObjectStart()
                }

                if (fieldRef != null) {
                    emitObjectKey(resolveFieldReference(fieldRef))
                }

                emitObjectStart()
                emitObjectKey(registerConstant('$' + named.name.canonical))
                emitContextValue(resolveValueReference(valueRef, fieldRef))
                emitObjectEnd()

                if (firstCall) {
                    emitObjectEnd()
                }
            } else if (setOf( // 1st basic attempt, to improve in INTELLIJ-77
                    Name.AND,
                    Name.OR,
                    Name.NOR,
                ).contains(named.name)
            ) {
                if (firstCall) {
                    emitObjectStart()
                }
                emitObjectKey(registerConstant('$' + named.name.canonical))
                emitArrayStart()
                hasChildren?.children?.forEach {
                    emitObjectStart()
                    emitQueryBody(it)
                    emitObjectEnd()
                    emitObjectValueEnd()
                }
                emitArrayEnd()
                if (firstCall) {
                    emitObjectEnd()
                }
            } else if (named.name == Name.NOT && hasChildren?.children?.size == 1) {
                // the not operator is a special case
                // because we receive it as:
                // $not: { $field$: $condition$ }
                // and it needs to be:
                // $field$: { $not: $condition$ }
                // we will do a JIT translation

                var innerChild = hasChildren.children.first()
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
            }
        }
    }

    return this
}

private fun <S> MongoshBackend.resolveValueReference(
    valueRef: HasValueReference<S>,
    fieldRef: HasFieldReference<S>?
) = when (val ref = valueRef.reference) {
    is HasValueReference.Constant -> registerConstant(ref.value)
    is HasValueReference.Runtime -> registerVariable(
        (fieldRef?.reference as? HasFieldReference.Known)?.fieldName ?: "<value>",
        ref.type
    )

    else -> registerVariable(
        "queryField",
        BsonAny
    )
}

private fun <S> MongoshBackend.resolveFieldReference(fieldRef: HasFieldReference<S>) =
    when (val ref = fieldRef.reference) {
        is HasFieldReference.Known -> registerConstant(ref.fieldName)
        is HasFieldReference.Unknown -> registerVariable("field", BsonAny)
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
