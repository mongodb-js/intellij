/**
 * Interface that needs to be implemented to access MongoDB. Implementation
 * classes must be thread safe.
 */

package com.mongodb.jbplugin.accessadapter

/**
 * A slice of data from MongoDB. S is the resulting type of the query.
 *
 * @see com.mongodb.jbplugin.accessadapter.slice.BuildInfo.Slice
 *
 * @param S
 */
interface Slice<S : Any> {
    val id: String
    suspend fun queryUsingDriver(from: MongoDbDriver): S
}

/**
 * Accessing MongoDB state will be done through the provider, that will ensure
 * efficient access or caching if necessary. The type `D` is the type of DataSource
 * that will be used by the slice. It's an opaque type.
 *
 * @param D
 */
interface MongoDbReadModelProvider<D> {
    fun <T : Any> slice(
        dataSource: D,
        slice: Slice<T>,
    ): T
}
