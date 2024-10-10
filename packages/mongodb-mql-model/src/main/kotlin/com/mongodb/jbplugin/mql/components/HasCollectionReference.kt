package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component
import com.mongodb.jbplugin.mql.Namespace

/**
 * @param S
 * @property reference
 */
data class HasCollectionReference<S>(
    val reference: CollectionReference<S>,
) : Component {
    data object Unknown : CollectionReference<Any> {
        inline fun <reified T> cast() = Unknown as T
    }

    /**
     * Makes a copy of HasCollectionReference after changing the underlying reference to Known
     * only if the underlying reference is  not Unknown
     *
     * @param database
     */
    fun copy(database: String): HasCollectionReference<S> = when (reference) {
        is Known -> copy(
            reference = Known(
                databaseSource = reference.databaseSource,
                collectionSource = reference.collectionSource,
                namespace = Namespace(database, reference.namespace.collection)
            )
        )

        is OnlyCollection -> copy(
            reference = Known(
                databaseSource = null,
                collectionSource = reference.collectionSource,
                namespace = Namespace(database, reference.collection)
            )
        )

        is Unknown -> this
    }

    /**
     * @param S
     */
    sealed interface CollectionReference<S>

    /**
     * @param S
     * @property namespace
     * @property databaseSource
     * @property collectionSource
     */
    data class Known<S>(
        val databaseSource: S?,
        val collectionSource: S,
        val namespace: Namespace,
    ) : CollectionReference<S>

    /**
     * @param S
     * @property collection
     * @property collectionSource
     */
    data class OnlyCollection<S>(
        val collectionSource: S,
        val collection: String,
    ) : CollectionReference<S>
}
