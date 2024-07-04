package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Namespace

/**
 * @property reference
 */
class HasCollectionReference(
    val reference: CollectionReference,
) {
    data object Unknown : CollectionReference
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
