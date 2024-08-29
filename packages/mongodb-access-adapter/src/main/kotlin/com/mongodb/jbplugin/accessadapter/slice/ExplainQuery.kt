package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.mql.Node
import org.bson.conversions.Bson

/**
 * Slice to be used when doing an explain query
 */
data class ExplainQuery(
    val result: Bson,
) {
    data class Slice(
        private val query: Node<*>,
    ) : com.mongodb.jbplugin.accessadapter.Slice<ExplainQuery> {
        override val id = "${javaClass.canonicalName}::$query"

        override suspend fun queryUsingDriver(from: MongoDbDriver): ExplainQuery {
            val result = from.explain(query)
            return ExplainQuery(result)
        }
    }
}
