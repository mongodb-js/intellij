package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component

/**
 * @property reference
 */
data class HasValueReference(
    val reference: ValueReference,
) : Component {
    data object Unknown : ValueReference
    sealed interface ValueReference

    /**
 * @property value
 * @property type
 */
data class Constant(
        val value: Any,
        val type: String,
    ) : ValueReference

    /**
 * @property type
 */
data class Runtime(
        val type: String,
    ) : ValueReference
}
