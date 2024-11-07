package com.mongodb.jbplugin.mql.parser.components

import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.parser.Parser

data object NoCollectionReference

inline fun <S, reified T : HasCollectionReference.CollectionReference<S>> collectionReference(): Parser<Node<S>, NoCollectionReference, T> {
    return { input ->
        when (val ref = input.component<HasCollectionReference<S>>()?.reference) {
            null -> Either.left(NoCollectionReference)
            is T -> Either.right(ref)
            else -> Either.left(NoCollectionReference)
        }
    }
}

fun <S> noCollection(): Parser<Node<S>, HasCollectionReference.CollectionReference<S>, Unit> {
    return { input ->
        when (val ref = input.component<HasCollectionReference<S>>()?.reference) {
            null -> Either.right(Unit)
            HasCollectionReference.Unknown -> Either.right(Unit)
            else -> Either.left(ref)
        }
    }
}
fun <S> knownCollection() = collectionReference<S, HasCollectionReference.Known<S>>()
fun <S> onlyCollection() = collectionReference<S, HasCollectionReference.OnlyCollection<S>>()
