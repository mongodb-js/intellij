package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.dialects.ConnectionContextExtractor
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.DialectParser

object MongoshDialect : Dialect<Any, Any> {
    override val parser: DialectParser<Any>
        get() = throw UnsupportedOperationException()

    override val formatter: DialectFormatter
        get() = MongoshDialectFormatter

    override val connectionContextExtractor: ConnectionContextExtractor<Any>
        get() = throw UnsupportedOperationException()

    override fun isUsableForSource(source: Any) = false
}