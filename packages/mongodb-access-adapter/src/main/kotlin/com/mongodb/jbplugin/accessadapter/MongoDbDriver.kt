/**
 * Represents the MongoDB Driver facade that we will use internally.
 * Usually, we won't use this class directly, only in tests. What we
 * will use is the MongoDBReadModelProvider, that provides caching
 * and safety mechanisms.
 *
 * @see com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
 */

package com.mongodb.jbplugin.accessadapter

import org.bson.conversions.Bson
import org.owasp.encoder.Encode
import java.net.URI
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Represents a MongoDB Namespace (db/coll)
 *
 * @property database
 * @property collection
 */
class Namespace private constructor(val database: String, val collection: String) {
    override fun toString(): String = "$database.$collection"

    override fun equals(other: Any?): Boolean = other is Namespace && hashCode() == other.hashCode()

    override fun hashCode(): Int = Objects.hash(database, collection)

    companion object {
        operator fun invoke(
            database: String,
            collection: String,
        ): Namespace =
            Namespace(
                Encode.forJavaScript(database),
                Encode.forJavaScript(collection),
            )
    }
}

/**
 * Represents the MongoDB Driver facade that we will use internally.
 * Usually, we won't use this class directly, only in tests. What we
 * will use is the MongoDBReadModelProvider, that provides caching
 * and safety mechanisms.
 *
 * @see com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
 */
interface MongoDbDriver {
    suspend fun serverUri(): URI

    suspend fun <T : Any> runCommand(
        database: String,
        command: Bson,
        result: KClass<T>,
        timeout: Duration = 1.seconds,
    ): T

    suspend fun <T : Any> findOne(
        namespace: Namespace,
        query: Bson,
        options: Bson,
        result: KClass<T>,
        timeout: Duration = 1.seconds,
    ): T?

    suspend fun <T : Any> findAll(
        namespace: Namespace,
        query: Bson,
        result: KClass<T>,
        limit: Int = 10,
        timeout: Duration = 1.seconds,
    ): List<T>

    suspend fun countAll(
        namespace: Namespace,
        query: Bson,
        timeout: Duration = 1.seconds,
    ): Long
}

/**
 * Converts a string in form of `db.coll` to a Namespace object.
 *
 * @return
 */
fun String.toNs(): Namespace {
    val (db, coll) = trim().split(".", limit = 2)
    return Namespace(db, coll)
}
