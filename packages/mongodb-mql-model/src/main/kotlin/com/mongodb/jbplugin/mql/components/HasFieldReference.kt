package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component

/**
 * @param S
 * @property reference
 */
data class HasFieldReference<S>(
    val reference: FieldReference<S>,
) : Component {
    /**
     * @param S
     */
    sealed interface FieldReference<S>

/**
 * @param S
 * @property source
 */
data class Unknown<S>(val source: S) : FieldReference<S>

    /**
     * @param S
     * @property fieldName
     * @property source
     */
    data class Known<S>(
        val source: S,
        val fieldName: String,
    ) : FieldReference<S>
}
