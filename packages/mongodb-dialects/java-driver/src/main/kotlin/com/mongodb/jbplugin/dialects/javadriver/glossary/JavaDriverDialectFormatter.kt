package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.mql.*

object JavaDriverDialectFormatter : DialectFormatter {
    override fun formatType(type: BsonType): String =
        when (type) {
            is BsonDouble -> "double"
            is BsonString -> "String"
            is BsonObject -> "Object"
            is BsonArray -> "List<${formatTypeNullable(type.schema)}>"
            is BsonObjectId -> "ObjectId"
            is BsonBoolean -> "boolean"
            is BsonDate -> "Date"
            is BsonNull -> "null"
            is BsonInt32 -> "int"
            is BsonInt64 -> "long"
            is BsonDecimal128 -> "BigDecimal"
            is BsonAny -> "any"
            is BsonAnyOf ->
                if (type.types.contains(BsonNull)) {
                    formatTypeNullable(BsonAnyOf(type.types - BsonNull))
                } else {
                    type.types
                        .map { formatType(it) }
                        .sorted()
                        .joinToString(" | ")
                }
            else -> "any"
        }

    private fun formatTypeNullable(type: BsonType): String =
        when (type) {
            is BsonDouble -> "Double"
            is BsonString -> "String"
            is BsonObject -> "Object"
            is BsonArray -> "List<${formatTypeNullable(type.schema)}>"
            is BsonObjectId -> "ObjectId"
            is BsonBoolean -> "Boolean"
            is BsonDate -> "Date"
            is BsonNull -> "null"
            is BsonInt32 -> "Integer"
            is BsonInt64 -> "Long"
            is BsonDecimal128 -> "BigDecimal"
            is BsonAny -> "any"
            is BsonAnyOf ->
                type.types
                    .map { formatTypeNullable(it) }
                    .sorted()
                    .joinToString(" | ")
            else -> "any"
        }
}
