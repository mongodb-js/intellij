package com.mongodb.jbplugin.mql.schema

sealed interface BsonType

data class AnyOf(val possibleTypes: Set<BsonType>) : BsonType

data class BsonDocument(val values: Map<String, BsonType>) : BsonType

data object BsonString : BsonType

data object BsonInt32 : BsonType

data object BsonNull : BsonType

class Collection(private val schema: BsonType) {
    fun typeOf(fieldPath: String): BsonType {
        fun recursivelyFindType(
            fieldPath: List<String>,
            root: BsonType,
        ): BsonType {
            if (fieldPath.isEmpty()) {
                return root
            }

            val currentField = fieldPath[0]
            val nextFieldPath = fieldPath.subList(1, fieldPath.size)

            when (root) {
                is BsonDocument -> {
                    return recursivelyFindType(nextFieldPath, root.values[currentField] ?: BsonNull)
                }
                is AnyOf -> {
                    val hasDoc = root.possibleTypes.find { it is BsonDocument } as BsonDocument?
                    if (hasDoc == null) {
                        return BsonNull
                    }

                    return AnyOf(
                        setOf(
                            BsonNull,
                            recursivelyFindType(nextFieldPath, hasDoc.values[currentField] ?: BsonNull),
                        ),
                    )
                }
                else -> return BsonNull
            }
        }

        return recursivelyFindType(fieldPath.split('.'), schema)
    }
}
