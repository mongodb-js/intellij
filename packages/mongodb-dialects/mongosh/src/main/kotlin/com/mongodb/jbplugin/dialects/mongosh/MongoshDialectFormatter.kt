package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.Node

object MongoshDialectFormatter : DialectFormatter {
    override fun <S> formatQuery(query: Node<S>, explain: Boolean) = ""
    override fun <S> indexCommandForQuery(query: Node<S>) = ""
    override fun formatType(type: BsonType) = ""
}