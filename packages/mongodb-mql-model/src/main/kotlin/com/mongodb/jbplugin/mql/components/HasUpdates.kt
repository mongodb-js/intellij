package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.HasChildren
import com.mongodb.jbplugin.mql.Node

/**
 * @param S
 * @property children
 */
data class HasUpdates<S>(
    override val children: List<Node<S>>,
) : HasChildren<S>
