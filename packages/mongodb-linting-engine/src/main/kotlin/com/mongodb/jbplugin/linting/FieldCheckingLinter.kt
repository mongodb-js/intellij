/**
 * Linter that checks that fields exist in the provided namespace.
 */

package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.linting.FieldCheckWarning.FieldDoesNotExist
import com.mongodb.jbplugin.mql.*
import com.mongodb.jbplugin.mql.components.HasChildren
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasValueReference

private typealias FieldCheckWarnings<S> = List<FieldCheckWarning<S>>

private typealias FieldAndValueReferences<S> = List<Reference<S>>

/**
 * Marker type for the result of the type.
 *
 * @see FieldDoesNotExist for warnings on fields do not existing.
 *
 * @param S
 */
sealed interface FieldCheckWarning<S> {
    /**
     * Warning that is emitted when the field does not exist in the provided namespace.
     *
     * @param S
     * @property field
     * @property namespace
     * @property source
     */
    data class FieldDoesNotExist<S>(
        val source: S,
        val field: String,
        val namespace: Namespace,
    ) : FieldCheckWarning<S>

    /**
     * Warning that is emitted when the BsonType of inspected
     * value does not match the BsonType of inspected field
     *
     * @param S
     * @property valueSource The source providing the field and value
     * for inspection
     * @property field Text value of the field being inspected
     * @property fieldType BsonType of inspected field
     * @property valueType BsonType of inspected value
     */
    data class FieldValueTypeMismatch<S>(
        val field: String,
        val fieldType: BsonType,
        val valueSource: S,
        val valueType: BsonType,
    ) : FieldCheckWarning<S>
}

/**
 * ADT that contains the result of the field check linter.
 *
 * @see FieldCheckWarning to understand a bit more the format of the result.
 *
 * @param S
 * @property warnings
 */
data class FieldCheckResult<S>(
    val warnings: FieldCheckWarnings<S>,
) {
    companion object {
        fun <S> empty(): FieldCheckResult<S> = FieldCheckResult(emptyList())
    }
}

/**
 * Linter that verifies that all fields that are referenced in a query do exist in the target collection.
 */
object FieldCheckingLinter {
    fun <D, S> lintQuery(
        dataSource: D,
        readModelProvider: MongoDbReadModelProvider<D>,
        query: Node<S>,
    ): FieldCheckResult<S> {
        val queryNamespace =
            query.component<HasCollectionReference<S>>() ?: return FieldCheckResult.empty()
        if (queryNamespace.reference !is HasCollectionReference.Known) {
            return FieldCheckResult.empty()
        }

        val namespace = (queryNamespace.reference as HasCollectionReference.Known).namespace
        val collection = readModelProvider.slice(
            dataSource,
            GetCollectionSchema.Slice(namespace)
        ).schema
        val fieldAndValueRefs = query.getAllFieldAndValueReferences()

        val warnings =
            fieldAndValueRefs.mapNotNull {
                when (it) {
                    is Reference.FieldReference -> it.toFieldExistenceWarning(collection, namespace)
                    is Reference.FieldValueReference ->
                        it.toFieldExistenceWarning(collection, namespace)
                            ?: it.toFieldValueTypeMismatchWarning(collection)
                }
            }

        return FieldCheckResult(warnings)
    }
}

/**
 * @param S
 */
sealed interface Reference<S> {
    /**
     * @param S
     * @property fieldSource
     * @property fieldName
     */
    data class FieldReference<S>(val fieldSource: S, val fieldName: String) : Reference<S> {
        fun toFieldExistenceWarning(
            collectionSchema: CollectionSchema,
            namespace: Namespace,
        ): FieldCheckWarning.FieldDoesNotExist<S>? {
            val fieldType = collectionSchema.typeOf(fieldName)
            return FieldCheckWarning.FieldDoesNotExist(
                fieldSource,
                fieldName,
                namespace
            ).takeIf { fieldType == BsonNull }
        }
    }

    /**
     * @param S
     * @property fieldSource
     * @property fieldName
     * @property valueSource
     * @property valueType
     */
    data class FieldValueReference<S>(
        val fieldSource: S,
        val fieldName: String,
        val valueSource: S,
        val valueType: BsonType
    ) : Reference<S> {
        fun toFieldExistenceWarning(
            collectionSchema: CollectionSchema,
            namespace: Namespace,
        ): FieldCheckWarning.FieldDoesNotExist<S>? {
            val fieldType = collectionSchema.typeOf(fieldName)
            return FieldCheckWarning.FieldDoesNotExist(
                fieldSource,
                fieldName,
                namespace
            ).takeIf { fieldType == BsonNull }
        }

        fun toFieldValueTypeMismatchWarning(
            collectionSchema: CollectionSchema,
        ): FieldCheckWarning.FieldValueTypeMismatch<S>? {
            val fieldType = collectionSchema.typeOf(fieldName)
            return FieldCheckWarning.FieldValueTypeMismatch(
                fieldName,
                fieldType,
                valueSource,
                valueType
            ).takeIf { !valueType.isAssignableTo(fieldType) }
        }
    }
}

private fun <S> Node<S>.getAllFieldAndValueReferences(): FieldAndValueReferences<S> {
    val hasChildren = component<HasChildren<S>>()
    val otherRefs =
        hasChildren?.children?.flatMap { it.getAllFieldAndValueReferences() } ?: emptyList()
    val fieldRef = component<HasFieldReference<S>>()?.reference ?: return otherRefs
    val valueRef = component<HasValueReference<S>>()?.reference
    return if (fieldRef is HasFieldReference.Known) {
        otherRefs + (
            valueRef?.let { reference ->
                when (reference) {
                    is HasValueReference.Constant<S> -> Reference.FieldValueReference(
                        fieldRef.source,
                        fieldRef.fieldName,
                        reference.source,
                        reference.type
                    )

                    is HasValueReference.Runtime<S> -> Reference.FieldValueReference(
                        fieldRef.source,
                        fieldRef.fieldName,
                        reference.source,
                        reference.type
                    )

                    else -> null
                }
            } ?: Reference.FieldReference(
                fieldRef.source,
                fieldRef.fieldName,
            )
            )
    } else {
        otherRefs
    }
}
