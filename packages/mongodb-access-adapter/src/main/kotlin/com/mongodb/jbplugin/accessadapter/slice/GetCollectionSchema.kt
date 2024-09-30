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
    data class Slice(
        private val namespace: Namespace,
    ) : com.mongodb.jbplugin.accessadapter.Slice<GetCollectionSchema> {
        override val id = "${javaClass.canonicalName}::$namespace"

        override suspend fun queryUsingDriver(from: MongoDbDriver): GetCollectionSchema {
            if (namespace.database.isBlank() || namespace.collection.isBlank()) {
                return GetCollectionSchema(
                    CollectionSchema(
                        namespace,
                        BsonObject(emptyMap())
                    )
                )
            }

            val sampleSomeDocs = from.findAll(
                namespace,
                Filters.empty(),
                Document::class,
                limit = 50
            )
            // we need to generate the schema from these docs
            val sampleSchemas = sampleSomeDocs.map(this::recursivelyBuildSchema)
            // now we want to merge them together
            val consolidatedSchema =
                sampleSchemas.reduceOrNull(::mergeSchemaTogether) ?: BsonObject(
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
                is Document -> BsonObject(
                    value.map {
                        it.key to recursivelyBuildSchema(it.value)
                    }.toMap()
                )
                is Map<*, *> -> BsonObject(
                    value.map {
                        it.key.toString() to
                            recursivelyBuildSchema(it.value)
                    }.toMap()
                )
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

                else -> primitiveOrWrapper(value.javaClass).toBsonType()
            }
    }
}
