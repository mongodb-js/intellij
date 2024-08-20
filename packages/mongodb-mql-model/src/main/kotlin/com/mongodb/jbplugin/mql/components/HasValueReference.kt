package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.Component

/**
 * @param S
 * @property reference
 */
data class HasValueReference<S>(
    val reference: ValueReference<S>,
) : Component {
    data object Unknown : ValueReference<Any>

    /**
     * @param S
     */
sealed interface ValueReference<S>

    /**
     * @param S
     * @property value
     * @property type
     * @property source
    */
    data class Constant<S>(
        val source: S,
        val value: Any?,
        val type: BsonType,
    ) : ValueReference<S>

    /**
     * @param S
     * @property type
     * @property source
    */
    data class Runtime<S>(
        val source: S,
        val type: BsonType,
    ) : ValueReference<S>
}
