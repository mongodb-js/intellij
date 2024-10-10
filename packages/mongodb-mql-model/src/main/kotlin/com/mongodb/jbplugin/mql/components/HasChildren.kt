package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component
import com.mongodb.jbplugin.mql.Node

/**
 * @param S
 * @property children
 */
@Deprecated("Use either HasFilter or HasUpdate", level = DeprecationLevel.ERROR)
data class HasChildren<S>(
    val children: List<Node<S>>,
) : Component
