package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.client.model.Filters
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.mql.*
import org.bson.Document

/**
 * Slice to be used when querying the schema of a given collection.
 *
 * @property schema
 */
data class GetCollectionSchema(
    val schema: CollectionSchema,
) {
    /**
     * @param namespace
     */
    class Slice(
        private val namespace: Namespace,
    ) : com.mongodb.jbplugin.accessadapter.Slice<GetCollectionSchema> {
        override val id = "GetCollectionSchema::$namespace"

        override suspend fun queryUsingDriver(from: MongoDbDriver): GetCollectionSchema {
            val sampleSomeDocs = from.findAll(namespace, Filters.empty(), Document::class, limit = 50)
            // we need to generate the schema from these docs
            val sampleSchemas = sampleSomeDocs.map(this::recursivelyBuildSchema)
            // now we want to merge them together
            val consolidatedSchema =
                sampleSchemas.reduceOrNull(this::mergeSchemaTogether) ?: BsonObject(
                    emptyMap(),
                )

            // flatten schema
            val schema = flattenAnyOfReferences(consolidatedSchema) as BsonObject
            return GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    schema,
                ),
            )
        }

        private fun recursivelyBuildSchema(value: Any?): BsonType =
            when (value) {
                null -> BsonNull
                is Document -> BsonObject(value.map { it.key to recursivelyBuildSchema(it.value) }.toMap())
                is Map<*, *> -> BsonObject(value.map { it.key.toString() to recursivelyBuildSchema(it.value) }.toMap())
                is Collection<*> -> recursivelyBuildSchema(value.toTypedArray())
                is Array<*> ->
                    BsonArray(
                        value
                            .map {
                                it?.javaClass?.toBsonType(it) ?: BsonNull
                            }.toSet()
                            .let {
                                if (it.size == 1) {
                                    it.first()
                                } else {
                                    BsonAnyOf(it)
                                }
                            },
                    )

                else -> value.javaClass.toBsonType()
            }

        private fun mergeSchemaTogether(
            first: BsonType,
            second: BsonType,
        ): BsonType {
            if (first is BsonObject && second is BsonObject) {
                val mergedMap =
                    first.schema.entries
                        .union(second.schema.entries)
                        .fold(mutableMapOf<String, BsonType>()) { acc, entry ->
                            acc.compute(entry.key) { _, current ->
                                current?.let {
                                    mergeSchemaTogether(current, entry.value)
                                } ?: entry.value
                            }

                            acc
                        }

                return BsonObject(mergedMap)
            }

            if (first is BsonArray && second is BsonArray) {
                return BsonArray(mergeSchemaTogether(first.schema, second.schema))
            }

            if (first is BsonAnyOf && second is BsonAnyOf) {
                return BsonAnyOf(first.types + second.types)
            }

            if (first is BsonAnyOf) {
                return BsonAnyOf(first.types + second)
            }

            if (second is BsonAnyOf) {
                return BsonAnyOf(second.types + first)
            }

            if (first == second) {
                return first
            }

            return BsonAnyOf(setOf(first, second))
        }

        private fun flattenAnyOfReferences(schema: BsonType): BsonType =
            when (schema) {
                is BsonArray -> BsonArray(flattenAnyOfReferences(schema.schema))
                is BsonObject ->
                    BsonObject(
                        schema.schema.entries.associate {
                            Pair(
                                it.key,
                                flattenAnyOfReferences(it.value),
                            )
                        },
                    )

                is BsonAnyOf -> {
                    val flattenAnyOf =
                        schema.types.flatMap {
                            val flattenType = flattenAnyOfReferences(it)
                            if (flattenType is BsonAnyOf) {
                                flattenType.types
                            } else {
                                listOf(flattenType)
                            }
                        }

                    BsonAnyOf(flattenAnyOf.toSet())
                }

                else -> schema
            }
    }
}
