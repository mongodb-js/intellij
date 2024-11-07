package com.mongodb.jbplugin.mql.parser.components

import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.parser.Parser

data object NoSourceDialect
fun <S> sourceDialect(): Parser<Node<S>, NoSourceDialect, HasSourceDialect.DialectName> {
    return { input ->
        when (val ref = input.component<HasSourceDialect>()) {
            null -> Either.left(NoSourceDialect)
            else -> Either.right(ref.name)
        }
    }
}
