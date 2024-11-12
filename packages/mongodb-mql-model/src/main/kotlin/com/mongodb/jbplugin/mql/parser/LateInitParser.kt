package com.mongodb.jbplugin.mql.parser

import com.mongodb.jbplugin.mql.adt.Either

class LateInitParser<I, E, O> {
    private lateinit var actualParser: Parser<I, E, O>

    fun init(parser: Parser<I, E, O>) {
        this.actualParser = parser
    }

    suspend fun invoke(i: I): Either<E, O> {
        return asParser().invoke(i)
    }

    fun asParser(): Parser<I, E, O> {
        return { input ->
            when (val result = actualParser.invoke(input)) {
                is Either.Left -> Either.left(result.value)
                is Either.Right -> Either.right(result.value)
            }
        }
    }
}

fun <I, E, O> lateInit(): LateInitParser<I, E, O> {
    return LateInitParser<I, E, O>()
}
