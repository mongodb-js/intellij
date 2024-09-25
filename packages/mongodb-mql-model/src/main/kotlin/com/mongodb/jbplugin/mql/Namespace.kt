package com.mongodb.jbplugin.mql

import java.util.*

/**
 * Represents a MongoDB Namespace (db/coll)
 *
 * @property database
 * @property collection
 */
class Namespace private constructor(
    val database: String,
    val collection: String,
) {
    val isValid = database.isNotBlank() && collection.isNotBlank()

    override fun toString(): String = "$database.$collection"

    override fun equals(other: Any?): Boolean = other is Namespace && hashCode() == other.hashCode()

    override fun hashCode(): Int = Objects.hash(database, collection)

    companion object {
        operator fun invoke(
            database: String,
            collection: String,
        ): Namespace =
            Namespace(
                database,
                collection,
            )
    }
}
