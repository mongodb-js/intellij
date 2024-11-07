package com.mongodb.jbplugin.mql.adt

sealed class Either<A, B> {
    data class Left<A>(val value: A) : Either<A, Nothing>()
    data class Right<B>(val value: B) : Either<Nothing, B>()

    companion object {
        fun <A, B> left(a: A): Either<A, B> = Left(a) as Either<A, B>
        fun <A, B> right(a: B): Either<A, B> = Right(a) as Either<A, B>
    }

    fun orElse(defVal: () -> B): B {
        return when (this) {
            is Left -> defVal()
            is Right -> value
        }
    }
}
