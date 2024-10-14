/**
 * The index analyzer is responsible for processing a query and return an index
 * that can cover the query correctly for efficiency.
 *
 * It can return different types of indexes depending on the query, but it will
 * suggest only one index.
 *
 * Right now it supports only MongoDB ordinary indexes, but it's open to suggest
 * also Search indexes in the future.
 **/

package com.mongodb.jbplugin.mql

import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasValueReference

/**
 * The IndexAnalyzer service itself. It's stateless and can be used directly.
 */
object IndexAnalyzer {
    /**
     * Analyses a query and return a suggested index. If no index can be inferred, it will
     * return NoIndex.
     *
     * @see SuggestedIndex
     *
     * @param query
     * @return
     */
    fun <S> analyze(query: Node<S>): SuggestedIndex<S> {
        val collectionRef =
            query.component<HasCollectionReference<S>>() ?: return SuggestedIndex.NoIndex.cast()
        val fields = query.allFieldReferences().distinctBy { it.first }
        val indexFields = fields.map { SuggestedIndex.MongoDbIndexField(it.first, it.second) }

        return SuggestedIndex.MongoDbIndex(collectionRef, indexFields)
    }

    private fun <S> Node<S>.allFieldReferences(): List<Pair<String, S>> {
        val hasFilter = component<HasFilter<S>>()
        val otherRefs = hasFilter?.children?.flatMap { it.allFieldReferences() } ?: emptyList()
        val fieldRef = component<HasFieldReference<S>>()?.reference ?: return otherRefs
        val valueRef = component<HasValueReference<S>>()?.reference
        return if (fieldRef is HasFieldReference.Known) {
            otherRefs + (
                valueRef?.let { reference ->
                    when (reference) {
                        is HasValueReference.Constant<S> -> Pair(
                            fieldRef.fieldName,
                            fieldRef.source
                        )

                        is HasValueReference.Runtime<S> -> Pair(
                            fieldRef.fieldName,
                            fieldRef.source
                        )

                        else -> null
                    }
                } ?: Pair(
                    fieldRef.fieldName,
                    fieldRef.source,
                )
                )
        } else {
            otherRefs
        }
    }

    /**
     * @param S
     */
    sealed interface SuggestedIndex<S> {
        @Suppress("UNCHECKED_CAST")
        data object NoIndex : SuggestedIndex<Any> {
            fun <S> cast(): SuggestedIndex<S> = this as SuggestedIndex<S>
        }

        /**
         * @param S
         * @property fieldName
         * @property source
         */
        data class MongoDbIndexField<S>(val fieldName: String, val source: S)

        /**
         * @param S
         * @property collectionReference
         * @property fields
         */
        data class MongoDbIndex<S>(
            val collectionReference: HasCollectionReference<S>,
            val fields: List<MongoDbIndexField<S>>
        ) : SuggestedIndex<S>
    }
}
