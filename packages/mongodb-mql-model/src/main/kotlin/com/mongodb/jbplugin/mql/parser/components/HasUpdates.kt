package com.mongodb.jbplugin.mql.parser.components

import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasUpdates
import com.mongodb.jbplugin.mql.parser.Parser

data object NoUpdates

fun <S> atLeastOneUpdate(): Parser<Node<S>, NoUpdates, List<Node<S>>> {
    return { input ->
        when (val ref = input.component<HasUpdates<S>>()) {
            null -> Either.left(NoUpdates)
            else -> if (ref.children.isNotEmpty()) {
                Either.right(ref.children)
            } else {
                Either.left(NoUpdates)
            }
        }
    }
}
