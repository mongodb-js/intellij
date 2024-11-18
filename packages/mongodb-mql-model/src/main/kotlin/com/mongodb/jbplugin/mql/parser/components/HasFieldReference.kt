package com.mongodb.jbplugin.mql.parser.components

import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.parser.Parser

data object NoFieldReference

inline fun <reified T : HasFieldReference.FieldReference<S>, S> fieldReference(): Parser<Node<S>, NoFieldReference, T> {
    return { input ->
        when (val ref = input.component<HasFieldReference<S>>()?.reference) {
            null -> Either.left(NoFieldReference)
            is T -> Either.right(ref)
            else -> Either.left(NoFieldReference)
        }
    }
}

fun <S> schemaFieldReference() = fieldReference<HasFieldReference.FromSchema<S>, S>()
