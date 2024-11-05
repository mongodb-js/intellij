package com.mongodb.jbplugin.mql.parser.components

import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.parser.Parser

data object NoValueReference

inline fun <reified T : HasValueReference.ValueReference<S>, S> valueReference(): Parser<Node<S>, NoValueReference, T> {
    return { input ->
        when (val ref = input.component<HasValueReference<S>>()?.reference) {
            null -> Either.left(NoValueReference)
            is T -> Either.right(ref)
            else -> Either.left(NoValueReference)
        }
    }
}

fun <S> constantValueReference() = valueReference<HasValueReference.Constant<S>, S>()
fun <S> runtimeValueReference() = valueReference<HasValueReference.Runtime<S>, S>()
