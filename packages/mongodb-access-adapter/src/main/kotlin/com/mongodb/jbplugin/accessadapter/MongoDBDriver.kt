package com.mongodb.jbplugin.accessadapter

import org.bson.Document
import org.owasp.encoder.Encode
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class Namespace(val database: String, val collection: String) {
    override fun toString(): String {
        return "${database}.${collection}"
    }
}

fun String.toNS(): Namespace {
    val (db, coll) = trim().split(".", limit = 2)
    return Namespace(
        Encode.forJavaScript(db),
        Encode.forJavaScript(coll)
    )
}

interface MongoDBDriver {
    suspend fun <T: Any> runCommand(command: Document, result: KClass<T>, timeout: Duration = 1.seconds): T
    suspend fun <T: Any> findOne(namespace: Namespace, query: Document, options: Document, result: KClass<T>, timeout: Duration = 1.seconds): T?
    suspend fun <T: Any> findAll(namespace: Namespace, query: Document, result: KClass<T>, limit: Int = 10, timeout: Duration = 1.seconds): List<T>
}