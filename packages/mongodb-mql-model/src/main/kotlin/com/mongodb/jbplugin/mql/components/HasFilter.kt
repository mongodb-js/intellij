package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Node

/**
 * @param S
 * @property filter
 */
data class HasFilter<S>(
    val filter: Node<S>,
)
