package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component

/**
 * @property reference
 */
class HasFieldReference(
    val reference: FieldReference,
) : Component {
    data object Unknown : FieldReference
    sealed interface FieldReference

    /**
 * @property fieldName
 */
data class Known(
        val fieldName: String,
    ) : FieldReference
}
