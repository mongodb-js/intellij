package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.dialects.mongosh.backend.DefaultContext
import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.mql.*
import org.bson.types.ObjectId
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

class MongoshBackendTest {
    @Test
    fun `generates a valid find query`() {
        assertGeneratedJs(
            """
            db.getSiblingDB("myDb").getCollection("myColl").find({ "field": 1})
            """.trimIndent()
        ) {
            emitDbAccess()
            emitDatabaseAccess(registerConstant("myDb"))
            emitCollectionAccess(registerConstant("myColl"))
            emitFunctionName("find")
            emitFunctionCall({
                emitObjectStart()
                emitObjectKey(registerConstant("field"))
                emitContextValue(registerConstant(1))
                emitObjectEnd()
            })
        }
    }

    @Test
    fun `generates a valid query with runtime parameters`() {
        assertGeneratedJs(
            """
            var myColl = ""
            var myDb = ""
            var myValue = ""

            db.getSiblingDB(myDb).getCollection(myColl).find({ "field": myValue})
""".trimIndent()
        ) {
            emitDbAccess()
            emitDatabaseAccess(registerVariable("myDb", BsonString))
            emitCollectionAccess(registerVariable("myColl", BsonString))
            emitFunctionName("find")
            emitFunctionCall({
                emitObjectStart()
                emitObjectKey(registerConstant("field"))
                emitContextValue(registerVariable("myValue", BsonString))
                emitObjectEnd()
            })
        }
    }

    @Test
    fun `generates a valid update query`() {
        assertGeneratedJs(
            """
            var myColl = ""
            var myDb = ""
            var myValue = ""

            db.getSiblingDB(myDb).getCollection(myColl).update({ "field": myValue}, { "myUpdate": 1})
""".trimIndent()
        ) {
            emitDbAccess()
            emitDatabaseAccess(registerVariable("myDb", BsonString))
            emitCollectionAccess(registerVariable("myColl", BsonString))
            emitFunctionName("update")
            emitFunctionCall({
                emitObjectStart()
                emitObjectKey(registerConstant("field"))
                emitContextValue(registerVariable("myValue", BsonString))
                emitObjectEnd()
            }, {
                emitObjectStart()
                emitObjectKey(registerConstant("myUpdate"))
                emitContextValue(registerConstant(1))
                emitObjectEnd()
            })
        }
    }

    @ParameterizedTest
    @MethodSource("bsonValues")
    fun `generates a valid bson object given a value`(testCase: Pair<Any, String>) {
        val (value, expected) = testCase
        assertGeneratedJs(
            expected
        ) {
            emitContextValue(registerConstant(value))
        }
    }

    @ParameterizedTest
    @MethodSource("bsonTypes")
    fun `generates a valid default object given the type of the value`(testCase: Pair<BsonType, String>) {
        val (type, expected) = testCase
        assertGeneratedJs(
            """
                var arg = $expected

                arg
            """.trimIndent()
        ) {
            emitContextValue(registerVariable("arg", type))
        }
    }

    companion object {
        @JvmStatic
        fun bsonValues(): Array<Pair<Any, String>> = arrayOf(
            1 to "1",
            1.5 to "1.5",
            "myString" to "\"myString\"",
            Date() to "Date()",
            BigInteger("5234") to "Decimal128(\"5234\")",
            BigDecimal("5234.5234") to "Decimal128(\"5234.5234\")",
            true to "true",
            ObjectId("66e02569aa5b362fa36f2416") to "ObjectId(\"66e02569aa5b362fa36f2416\")",
            listOf(1, 2.2, Date()) to "[1, 2.2, Date()]",
            mapOf("a" to "1", "b" to 2) to "{\"a\": \"1\", \"b\": 2}",
            SomeObject(1, "2") to "{}", // we won't serialize unknown objects
        )

        @JvmStatic
        fun bsonTypes(): Array<Pair<BsonType, String>> = arrayOf(
            BsonAny to "\"any\"",
            BsonAnyOf(BsonNull, BsonString) to "\"\"",
            BsonAnyOf(BsonNull, BsonString, BsonInt64) to "\"\"",
            BsonArray(BsonAny) to "[]",
            BsonBoolean to "false",
            BsonDate to "Date()",
            BsonDecimal128 to "Decimal128(\"0\")",
            BsonDouble to "0.0",
            BsonInt32 to "0",
            BsonInt64 to "0",
            BsonNull to "null",
            BsonObject(emptyMap()) to "{}",
            BsonObjectId to "ObjectId(\"000000000000000000000000\")",
            BsonString to "\"\"",
        )

        private data class SomeObject(val exampleInt: Int, val exampleString: String)
    }
}

private fun assertGeneratedJs(@Language("js") js: String, script: MongoshBackend.() -> MongoshBackend) {
    val generated = script(MongoshBackend(DefaultContext())).computeOutput()
    assertEquals(js, generated)
}