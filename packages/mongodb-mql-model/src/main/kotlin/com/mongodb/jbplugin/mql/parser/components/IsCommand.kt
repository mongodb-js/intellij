package com.mongodb.jbplugin.mql.parser.components

import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.parser.Parser

data object CommandDoesNotMatch
fun <S> whenIsCommand(cmd: IsCommand.CommandType): Parser<Node<S>, CommandDoesNotMatch, Node<S>> {
    return { input ->
        when (input.component<IsCommand>()?.type) {
            null -> Either.left(CommandDoesNotMatch)
            cmd -> Either.right(input)
            else -> Either.left(CommandDoesNotMatch)
        }
    }
}

fun <S> whenHasAnyCommand(): Parser<Node<S>, CommandDoesNotMatch, Node<S>> {
    return { input ->
        when (input.component<IsCommand>()?.type) {
            null -> Either.left(CommandDoesNotMatch)
            else -> Either.right(input)
        }
    }
}
