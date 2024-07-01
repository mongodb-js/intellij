package com.mongodb.jbplugin.mql

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class BsonTypeTest {
    @ParameterizedTest
    @MethodSource("java to bson")
    fun `should map correctly all java types`(
        javaClass: Class<*>,
        expected: BsonType,
    ) {
        assertEquals(expected, javaClass.toBsonType())
    }
    companion object {
        @JvmStatic
        fun `java to bson`(): Array<Any> =
            arrayOf(
                arrayOf(Double::class.javaObjectType, BsonAnyOf(BsonNull, BsonDouble)),
                arrayOf(Double::class.javaPrimitiveType, BsonDouble),
                arrayOf(CharSequence::class.java, BsonAnyOf(BsonNull, BsonString)),
                arrayOf(String::class.java, BsonAnyOf(BsonNull, BsonString)),
                arrayOf(Boolean::class.javaObjectType, BsonAnyOf(BsonNull, BsonBoolean)),
                arrayOf(Boolean::class.javaPrimitiveType, BsonBoolean),
                arrayOf(Date::class.java, BsonAnyOf(BsonNull, BsonDate)),
                arrayOf(Instant::class.java, BsonAnyOf(BsonNull, BsonDate)),
                arrayOf(LocalDate::class.java, BsonAnyOf(BsonNull, BsonDate)),
                arrayOf(LocalDateTime::class.java, BsonAnyOf(BsonNull, BsonDate)),
                arrayOf(Int::class.javaObjectType, BsonAnyOf(BsonNull, BsonInt32)),
                arrayOf(Int::class.javaPrimitiveType, BsonInt32),
                arrayOf(BigInteger::class.java, BsonAnyOf(BsonNull, BsonInt64)),
                arrayOf(BigDecimal::class.java, BsonAnyOf(BsonNull, BsonDecimal128)),
                arrayOf(ArrayList::class.java, BsonAnyOf(BsonNull, BsonArray(BsonAny))),
                arrayOf(
                    ExampleClass::class.java,
                    BsonAnyOf(
                        BsonNull,
                        BsonObject(
                            mapOf(
                                "field" to BsonAnyOf(BsonNull, BsonString),
                            ),
                        ),
                    ),
                ),
            )
        data class ExampleClass(
            val field: String,
        )
}
}
