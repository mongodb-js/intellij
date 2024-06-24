package com.mongodb.jbplugin.dialects

import com.mongodb.jbplugin.mql.ast.Node

interface DialectParser<S, D : Dialect<S>> {
    fun canParse(source: S): Boolean

    fun attachment(source: S): S

    fun parse(source: S): Node<S>
}
