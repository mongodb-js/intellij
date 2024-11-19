package com.mongodb.jbplugin.mql.parser.components

import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.parser.Parser

data object NoFilters

fun <S> atLeastOneFilter(): Parser<Node<S>, NoFilters, List<Node<S>>> {
    return { input ->
        when (val ref = input.component<HasFilter<S>>()) {
            null -> Either.left(NoFilters)
            else -> if (ref.children.isNotEmpty()) {
                Either.right(ref.children)
            } else {
                Either.left(NoFilters)
            }
        }
    }
}

fun <S> allFiltersRecursively(): Parser<Node<S>, NoFilters, List<Node<S>>> {
    return { input ->
        fun gather(el: Node<S>): List<Node<S>> {
            return when (val ref = el.component<HasFilter<S>>()) {
                null -> emptyList()
                else -> ref.children + ref.children.flatMap(::gather)
            }
        }

        val aggregation = input.component<HasAggregation<S>>()
        val aggregationStages = aggregation?.children ?: emptyList()
        val result = gather(input) + aggregationStages.flatMap(::gather)
        if (result.isEmpty()) {
            Either.left(NoFilters)
        } else {
            Either.right(result)
        }
    }
}
