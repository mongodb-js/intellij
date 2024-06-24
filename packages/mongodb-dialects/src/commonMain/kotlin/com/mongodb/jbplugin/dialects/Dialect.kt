package com.mongodb.jbplugin.dialects

interface Dialect<S> {
    val parser: DialectParser<S, Dialect<S>>
}
