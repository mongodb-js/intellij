/**
 * Linter that checks that fields exist in the provided namespace.
 */

package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasChildren
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasValueReference

private typealias FieldCheckWarnings<S> = List<FieldCheckWarning<S>>

private typealias FieldAndReferences<S> = List<FieldValueTypeAndReference<S>>

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
        val queryNamespace = query.component<HasCollectionReference>() ?: return FieldCheckResult.empty()
        if (queryNamespace.reference !is HasCollectionReference.Known) {
            return FieldCheckResult.empty()
        }

        val namespace = (queryNamespace.reference as HasCollectionReference.Known).namespace
        val collection = readModelProvider.slice(dataSource, GetCollectionSchema.Slice(namespace)).schema
        val allFieldReferences = query.getAllFieldValueTypeAndReferences()

        val warnings =
            allFieldReferences.mapNotNull {
                val fieldType = collection.typeOf(it.field)
                if (fieldType == BsonNull) {
                    FieldCheckWarning.FieldDoesNotExist(it.fieldReference, it.field, namespace)
                } else if (it.valueType != null && !it.valueType.isAssignableTo(fieldType)) {
                    FieldCheckWarning.FieldValueTypeMismatch(it.field, fieldType, it.valueReference, it.valueType)
                } else {
                    null
                }
            }

        return FieldCheckResult(warnings)
    }
}

/**
 * @param S
 * @property fieldReference
 * @property field
 * @property valueReference
 * @property valueType
 */
private data class FieldValueTypeAndReference<S>(
    val fieldReference: S,
    val field: String,
    val valueReference: S,
    val valueType: BsonType?,
)

private fun <S> Node<S>.getAllFieldValueTypeAndReferences(): FieldAndReferences<S> {
    val hasChildren = component<HasChildren<S>>()
    val otherRefs = hasChildren?.children?.flatMap { it.getAllFieldValueTypeAndReferences() } ?: emptyList()
    val fieldRef = component<HasFieldReference<S>>()?.reference ?: return otherRefs
    return if (fieldRef is HasFieldReference.Known) {
        val (valueSource, valueType) = component<HasValueReference<S>>()?.reference?.let {
            when (it) {
                is HasValueReference.Constant -> it.source to it.type
                is HasValueReference.Runtime -> it.source to it.type
                else -> return otherRefs
            }
        } ?: return otherRefs

        otherRefs + FieldValueTypeAndReference(
            fieldRef.source,
            fieldRef.fieldName,
            valueSource,
            valueType
        )
    } else {
        otherRefs
    }
}
