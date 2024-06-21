package com.mongodb.jbplugin.dialects

import com.mongodb.jbplugin.mql.ast.Node

interface DialectParser<S, D : Dialect<S>> {
    suspend fun canParse(source: S): Boolean

    suspend fun parse(source: S): Node<S>
}
