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
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.parser.anyError
import com.mongodb.jbplugin.mql.parser.components.NoFieldReference
import com.mongodb.jbplugin.mql.parser.components.aggregationStages
import com.mongodb.jbplugin.mql.parser.components.allNodesWithSchemaFieldReferences
import com.mongodb.jbplugin.mql.parser.components.hasName
import com.mongodb.jbplugin.mql.parser.components.schemaFieldReference
import com.mongodb.jbplugin.mql.parser.filter
import com.mongodb.jbplugin.mql.parser.first
import com.mongodb.jbplugin.mql.parser.flatMap
import com.mongodb.jbplugin.mql.parser.map
import com.mongodb.jbplugin.mql.parser.mapError
import com.mongodb.jbplugin.mql.parser.mapMany
import com.mongodb.jbplugin.mql.parser.matches
import com.mongodb.jbplugin.mql.parser.nth
import com.mongodb.jbplugin.mql.parser.parse
import com.mongodb.jbplugin.mql.parser.requireNonNull

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
        val extractFieldReference = schemaFieldReference<S>()
            .map { it.displayName to it.source }
            .mapError { NoFieldReference }

        val extractAllFieldReferencesWithValues = allNodesWithSchemaFieldReferences<S>()
            .mapMany(extractFieldReference)

        val extractFromFirstMatchStage = aggregationStages<S>()
            .nth(0)
            .matches(hasName(Name.MATCH))
            .flatMap(extractAllFieldReferencesWithValues)
            .anyError()

        val extractFromFiltersWhenNoAggregation = requireNonNull<Node<S>, Any>(Unit)
            .matches(aggregationStages<S>().filter { it.isEmpty() }.matches())
            .flatMap(extractAllFieldReferencesWithValues)
            .anyError()

        val findIndexableFieldReferences = first(
            extractFromFirstMatchStage,
            extractFromFiltersWhenNoAggregation,
        )

        return findIndexableFieldReferences
            .parse(this)
            .orElse { emptyList() }
    }

    /**
     * @param S
     */
    sealed interface SuggestedIndex<S> {
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
