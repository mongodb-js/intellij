package com.mongodb.accessadapter

data class VectorSearchDefinitionField(
    val type: String,
    val path: String,
    val numDimensions: Int,
    val similarity: String
)

data class VectorSearchDefinition(
    val name: String,
    val collectionName: String,
    val database: String,
    val type: String,
    val fields: List<VectorSearchDefinitionField>
) {
    companion object {
        fun newSingleField(database: String, collection: String, field: String, dimensions: Int, similarity: String): VectorSearchDefinition {
            return VectorSearchDefinition("default", collection, database, "vectorSearch", listOf(
                VectorSearchDefinitionField("vector", field, dimensions, similarity)
            ))
        }
    }
}

interface SearchIndexProvider {
    suspend fun createVectorSearchIndex(groupId: String, clusterName: String, index: VectorSearchDefinition): Unit
}