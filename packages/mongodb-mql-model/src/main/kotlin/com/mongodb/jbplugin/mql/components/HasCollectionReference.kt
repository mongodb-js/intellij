package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component
import com.mongodb.jbplugin.mql.Namespace

/**
 * @property reference
 */
data class HasCollectionReference(
    val reference: CollectionReference,
) : Component {
    data object Unknown : CollectionReference

    /**
     * Makes a copy of HasCollectionReference after changing the underlying reference to Known
     * only if the underlying reference is  not Unknown
     *
     * @param database
     */
    fun copy(database: String): HasCollectionReference = when (reference) {
        is Known -> copy(reference = Known(Namespace(database, reference.namespace.collection)))
        is OnlyCollection -> copy(reference = Known(Namespace(database, reference.collection)))
        is Unknown -> copy(reference = reference)
    }

    sealed interface CollectionReference

    /**
     * @property namespace
     */
    data class Known(
        val namespace: Namespace,
    ) : CollectionReference

    /**
     * @property collection
     */
    data class OnlyCollection(
        val collection: String,
    ) : CollectionReference
}
