/**
 * Autocompletion engine. This file is the facade and entry point to the engine.
 */

package com.mongodb.jbplugin.autocomplete

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.Namespace

/**
 * Represents one of the autocompletion elements.
 *
 * @property entry
 * @property type
 * @property bsonType
 */
data class AutocompletionEntry(
    val entry: String,
    val type: AutocompletionEntryType,
    val bsonType: BsonType?,
) {
    /**
     * @property presentableName
     */
    enum class AutocompletionEntryType(
        val presentableName: String,
    ) {
        DATABASE("MongoDB Database"),
        COLLECTION("MongoDB Collection"),
        FIELD("MongoDB Field"),
    }
}

/**
 * Represent the different types of results of the autocomplete engine.
 */
sealed interface AutocompletionResult {
    /**
     * There are autocompletion entries.
     *
     * @property entries
     */
    data class Successful(
        val entries: List<AutocompletionEntry>,
    ) : AutocompletionResult

    /**
     * We don't have a model for the provided namespace, so we can't autocomplete fields.
     *
     * @see Autocompletion.autocompleteFields
     * @property namespace
     */
    data class NoModel(
        val namespace: Namespace,
    ) : AutocompletionResult

    /**
     * The provided database does not exist.
     *
     * @see Autocompletion.autocompleteCollections
     * @property database
     */
    data class DatabaseDoesNotExist(
        val database: String,
    ) : AutocompletionResult
}

/**
 * Autocompletion facade. Use the methods in this class to get all the required autocompletion information.
 */
object Autocompletion {
    fun <D> autocompleteDatabases(
        dataSource: D,
        readModelProvider: MongoDbReadModelProvider<D>,
    ): AutocompletionResult {
        val listDatabases = readModelProvider.slice(dataSource, ListDatabases.Slice)
        val entries =
            listDatabases.databases.map {
                AutocompletionEntry(
                    it.name,
                    AutocompletionEntry.AutocompletionEntryType.DATABASE,
                    bsonType = null
                )
            }

        return AutocompletionResult.Successful(entries)
    }

    fun <D> autocompleteCollections(
        dataSource: D,
        readModelProvider: MongoDbReadModelProvider<D>,
        database: String,
    ): AutocompletionResult {
        val listCollections = runCatching {
            readModelProvider.slice(dataSource, ListCollections.Slice(database))
        }
            .getOrNull()
        listCollections ?: return AutocompletionResult.DatabaseDoesNotExist(database)

        val entries =
            listCollections.collections.map {
                AutocompletionEntry(
                    it.name,
                    AutocompletionEntry.AutocompletionEntryType.COLLECTION,
                    bsonType = null
                )
            }

        return AutocompletionResult.Successful(entries)
    }

    fun <D> autocompleteFields(
        dataSource: D,
        readModelProvider: MongoDbReadModelProvider<D>,
        namespace: Namespace,
    ): AutocompletionResult {
        val schema = runCatching {
            readModelProvider.slice(dataSource, GetCollectionSchema.Slice(namespace)).schema
        }
            .getOrNull()
        schema ?: return AutocompletionResult.NoModel(namespace)

        val entries =
            schema.allFieldNamesQualified().map {
                AutocompletionEntry(
                    it.first,
                    AutocompletionEntry.AutocompletionEntryType.FIELD,
                    bsonType = it.second
                )
            }

        return AutocompletionResult.Successful(entries)
    }
}
