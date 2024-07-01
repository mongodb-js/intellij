package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component
import com.mongodb.jbplugin.mql.Node

/**
 * @param S
 * @property children
 */
data class HasChildren<S>(
    val children: List<Node<S>>,
) : Component
