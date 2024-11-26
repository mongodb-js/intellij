/**
 * Linter that checks that the database and collection specified in the query do exist
 * in the current datasource.
 */

package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.linting.NamespaceCheckWarning.CollectionDoesNotExist
import com.mongodb.jbplugin.linting.NamespaceCheckWarning.DatabaseDoesNotExist
import com.mongodb.jbplugin.linting.NamespaceCheckWarning.NoNamespaceInferred
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.parser.*
import com.mongodb.jbplugin.mql.parser.components.knownCollection
import com.mongodb.jbplugin.mql.parser.components.noCollection
import com.mongodb.jbplugin.mql.parser.components.onlyCollection

/**
 * Marker type for the result of the linter.
 *
 * @see CollectionDoesNotExist as an example implementation.
 *
 * @param S Source type of the query (for intellij it's PsiElement)
 */
sealed interface NamespaceCheckWarning<S> {
    /**
     * If we couldn't find a namespace for this query.
     *
     * @param S
     * @property source
     */
    data class NoNamespaceInferred<S>(
        val source: S,
    ) : NamespaceCheckWarning<S>

    /**
     * If the provided database does not exist. Will only happen in dialects where the database
     * can be specified through code, like the Java Driver.
     *
     * @param S
     * @property source
     * @property database
     */
    data class DatabaseDoesNotExist<S>(
        val source: S,
        val database: String
    ) : NamespaceCheckWarning<S>

    /**
     * If the provided collection does not exist in the current database.
     *
     * @param S
     * @property source
     * @property database
     * @property collection
     */
    data class CollectionDoesNotExist<S>(
        val source: S,
        val database: String,
        val collection: String
    ) : NamespaceCheckWarning<S>
}

/**
 * Wrapper ADT type of warnings that contains the results of the linter.
 *
 * @see NamespaceCheckingLinter
 *
 * @param S
 * @property warnings
 */
data class NamespaceCheckResult<S>(
    val warnings: List<NamespaceCheckWarning<S>>
)

/**
 * Linter that verifies that the specified database and collection in the current query does exist
 * in the connected data source.
 */
object NamespaceCheckingLinter {
    fun <D, S> lintQuery(
        dataSource: D,
        readModelProvider: MongoDbReadModelProvider<D>,
        query: Node<S>,
    ): NamespaceCheckResult<S> {
        val dbList = readModelProvider.slice(dataSource, ListDatabases.Slice)

        val databaseDoesNotExistParser = knownCollection<S>()
            .filter { databaseDoesNotExist(dbList, it) }
            .map {
                DatabaseDoesNotExist(
                    source = it.databaseSource!!,
                    database = it.namespace.database,
                )
            }.mapAs<NamespaceCheckWarning<S>, _, _, _>()
            .map(::listOf)
            .anyError()

        val collectionDoesNotExistParser = knownCollection<S>()
            .filter { collectionDoesNotExist(readModelProvider, dataSource, it) }
            .map {
                CollectionDoesNotExist(
                    source = it.collectionSource!!,
                    database = it.namespace.database,
                    collection = it.namespace.collection
                )
            }.mapAs<NamespaceCheckWarning<S>, _, _, _>()
            .map(::listOf)
            .anyError()

        val databaseIsNotKnown = onlyCollection<S>()
            .filter { it.collection.isNotEmpty() }
            .map { NoNamespaceInferred(source = it.collectionSource) }
            .mapAs<NamespaceCheckWarning<S>, _, _, _>()
            .map(::listOf)
            .anyError()

        val noCollectionSpecified = noCollection<S>()
            .map { NoNamespaceInferred(source = query.source) }
            .mapAs<NamespaceCheckWarning<S>, _, _, _>()
            .map(::listOf)
            .anyError()

        val namespaceErrorsParser = first(
            databaseDoesNotExistParser,
            collectionDoesNotExistParser,
            databaseIsNotKnown,
            noCollectionSpecified,
            otherwise { emptyList() }
        )

        return NamespaceCheckResult(
            namespaceErrorsParser.parse(query).orElse { emptyList() }
        )
    }

    private fun <D, S> collectionDoesNotExist(
        readModelProvider: MongoDbReadModelProvider<D>,
        dataSource: D,
        ref: HasCollectionReference.Known<S>
    ): Boolean = !runCatching {
        readModelProvider.slice(
            dataSource,
            ListCollections.Slice(ref.namespace.database)
        ).collections.map { it.name }
    }.getOrDefault(emptyList())
        .contains(ref.namespace.collection)

    private fun <S> databaseDoesNotExist(
        dbList: ListDatabases,
        known: HasCollectionReference.Known<S>
    ): Boolean = dbList.databases.find { database -> known.namespace.database == database.name } ==
        null
}
