package com.mongodb.jbplugin.mql.parser.components

import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.parser.Parser

fun <S> aggregationStages(): Parser<Node<S>, Any, List<Node<S>>> {
    return { input ->
        val aggr = input.component<HasAggregation<S>>()
        if (aggr == null) {
            Either.right(emptyList())
        } else {
            Either.right(aggr.children)
        }
    }
}
