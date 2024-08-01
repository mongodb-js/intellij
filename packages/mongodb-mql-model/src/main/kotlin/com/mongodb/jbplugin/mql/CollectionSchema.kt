package com.mongodb.jbplugin.mql

import org.intellij.lang.annotations.Language
import java.lang.Integer.parseInt

/**
 * @property namespace
 * @property schema
 */
data class CollectionSchema(
    val namespace: Namespace,
    val schema: BsonObject,
) {
    fun typeOf(
        @Language("JSONPath") jsonPath: String,
    ): BsonType {
        val splitJsonPath = jsonPath.split('.').toList()
        return recursivelyGetType(splitJsonPath, schema)
    }

    fun allFieldNamesQualified(): List<Pair<String, BsonType>> = recursivelyAllFieldNamesQualified(emptyList(), schema)

    private fun recursivelyAllFieldNamesQualified(
        currentPath: List<String>,
        root: BsonType,
    ): List<Pair<String, BsonType>> =
        when (root) {
            is BsonArray -> recursivelyAllFieldNamesQualified(currentPath + "0", root.schema)
            is BsonObject -> root.schema.flatMap { recursivelyAllFieldNamesQualified(currentPath + it.key, it.value) }
            is BsonAnyOf ->
                root.types
                    .flatMap {
                        recursivelyAllFieldNamesQualified(currentPath, it)
                    }
.fold(emptySet<BsonType>()) { acc, el ->
                        acc + el.second
                    }
.let {
                        listOf(Pair(currentPath.joinToString("."), BsonAnyOf(it)))
                    }
            else -> listOf(Pair(currentPath.joinToString("."), root))
        }

    private fun recursivelyGetType(
        jsonPath: List<String>,
        root: BsonType,
    ): BsonType {
        if (jsonPath.isEmpty()) {
            return root
        }

        val current = jsonPath.first()
        val isCurrentNumber = kotlin.runCatching { parseInt(current) }.isSuccess

        val listOfOptions = mutableListOf<BsonType>()

        when (root) {
            is BsonArray ->
                if (isCurrentNumber) {
                    val childType = recursivelyGetType(jsonPath.subList(1, jsonPath.size), root.schema)
                    listOfOptions.add(childType)
                } else {
                    listOfOptions.add(BsonNull)
                }
            is BsonObject -> {
                val objectType = root.schema[jsonPath[0]]
                listOfOptions.add(
                    objectType?.let {
                        recursivelyGetType(jsonPath.subList(1, jsonPath.size), objectType)
                    } ?: BsonNull,
                )
            }
            is BsonAnyOf ->
                listOfOptions.addAll(
                    root.types.map { recursivelyGetType(jsonPath, it) },
                )
            else -> listOfOptions.add(BsonNull)
        }

        return if (listOfOptions.size == 1) {
            listOfOptions.first()
        } else {
            BsonAnyOf(listOfOptions.toSet())
        }
    }
}
