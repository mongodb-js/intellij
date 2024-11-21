package com.mongodb.jbplugin.mql.parser.components

import com.mongodb.jbplugin.mql.HasChildren
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.parser.Parser

data object NoFieldReference

data object NoFieldReferences

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

fun <S> allNodesWithSchemaFieldReferences(): Parser<Node<S>, NoFieldReferences, List<Node<S>>> {
    return { input ->
        fun gatherSchemaFieldReferenceNodes(node: Node<S>): List<Node<S>> {
            val isSchemaFieldReference = node.component<HasFieldReference<S>>()?.reference is HasFieldReference.FromSchema<S>

            val currentNode = if (isSchemaFieldReference) listOf(node) else emptyList()

            val childNodes = node.componentsWithChildren()
                .flatMap { (it as HasChildren<S>).children }
                .flatMap(::gatherSchemaFieldReferenceNodes)

            return currentNode + childNodes
        }

        val result = gatherSchemaFieldReferenceNodes(input)

        if (result.isEmpty()) {
            Either.left(NoFieldReferences)
        } else {
            Either.right(result)
        }
    }
}
