/**
 * A slice that represents the build information of the connected cluster.
 */

package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.ExplainPlan
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.mql.Node

/**
 * Runs the explain plan of a query.
 *
 * @property explainPlan
 */
data class ExplainQuery(
    val explainPlan: ExplainPlan
) {
/**
 * @param S
 * @property query
 */
data class Slice<S>(val query: Node<S>) : com.mongodb.jbplugin.accessadapter.Slice<ExplainQuery> {
        override val id = "${javaClass.canonicalName}::$query"

        override suspend fun queryUsingDriver(from: MongoDbDriver): ExplainQuery {
            val plan = from.explain(query)
            return ExplainQuery(plan)
        }
    }
}