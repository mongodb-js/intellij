package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.mongodb.jbplugin.mql.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class JavaDriverDialectFormatterTest {
    @ParameterizedTest
    @MethodSource("bsonTypesToJava")
    fun `should map BSON types to the java representation`(
        bsonType: BsonType,
        javaType: String,
    ) {
        assertEquals(
            javaType,
            JavaDriverDialectFormatter.formatType(bsonType),
        )
    }
    companion object {
        @JvmStatic
        fun bsonTypesToJava(): Array<Array<Any>> =
            arrayOf(
                arrayOf(BsonDouble, "double"),
                arrayOf(BsonString, "String"),
                arrayOf(BsonObject(emptyMap()), "Object"),
                arrayOf(BsonArray(BsonDouble), "List<Double>"),
                arrayOf(BsonObjectId, "ObjectId"),
                arrayOf(BsonBoolean, "boolean"),
                arrayOf(BsonDate, "Date"),
                arrayOf(BsonNull, "null"),
                arrayOf(BsonInt32, "int"),
                arrayOf(BsonInt64, "long"),
                arrayOf(BsonDecimal128, "BigDecimal"),
                arrayOf(BsonAny, "any"),
                arrayOf(BsonAnyOf(BsonNull, BsonDouble), "Double"), // java boxed value
                arrayOf(BsonAnyOf(BsonInt32, BsonDouble), "double | int"),
            )
    }
}
