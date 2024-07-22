/**
 * Linter that checks that fields exist in the provided namespace.
 */

package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasChildren
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference

private typealias FieldCheckWarnings<S> = List<FieldCheckWarning<S>>

private typealias FieldAndReferences<S> = List<FieldAndReference<S>>

/**
 * Marker type for the result of the type.
 *
 * @see FieldDoesNotExist for warnings on fields do not existing.
 *
 * @param S
 */
sealed interface FieldCheckWarning<S> {
    /**
     * Warning that is emitted when the field does not exist in the provided namespace.* @param S
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
        val allFieldReferences = query.getAllFieldReferences()

        val warnings =
            allFieldReferences.mapNotNull {
                if (collection.typeOf(it.field) == BsonNull) {
                    FieldCheckWarning.FieldDoesNotExist(it.reference, it.field, namespace)
                } else {
                    null
                }
            }

        return FieldCheckResult(warnings)
    }
}

/**
 * @param S
 * @property reference
 * @property field
 */
private data class FieldAndReference<S>(
    val reference: S,
    val field: String,
)

private fun <S> Node<S>.getAllFieldReferences(): FieldAndReferences<S> {
    val hasChildren = component<HasChildren<S>>()
    val otherRefs = hasChildren?.children?.flatMap { it.getAllFieldReferences() } ?: emptyList()
    val fieldRef = component<HasFieldReference<S>>()?.reference ?: return otherRefs
    return if (fieldRef is HasFieldReference.Known) {
        otherRefs + FieldAndReference(fieldRef.source, fieldRef.fieldName)
    } else {
        otherRefs
    }
}
