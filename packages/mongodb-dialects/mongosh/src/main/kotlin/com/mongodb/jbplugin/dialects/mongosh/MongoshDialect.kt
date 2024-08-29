package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.dialects.ConnectionContextExtractor
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.DialectParser

object MongoshDialect : Dialect<Unit, Unit> {
    override val parser: DialectParser<Unit>
        get() = throw UnsupportedOperationException()

    override val formatter: DialectFormatter
        get() = MongoshDialectFormatter

    override val connectionContextExtractor: ConnectionContextExtractor<Unit>?
        get() = null

    override fun isUsableForSource(source: Unit) = false
}