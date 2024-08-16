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

    @ParameterizedTest
    @MethodSource(
        "simple BsonType match assertions",
        "BsonAnyOf match assertions",
        "BsonArray match assertions",
        "BsonObject match assertions",
    )
    fun `should calculate correctly whether a type matches the other provided type`(
        type: BsonType,
        otherType: BsonType,
        expectedToMatch: Boolean,
    ) {
        val assertionFailureMessage = if (expectedToMatch) {
            "$type was expected to match $otherType but did not"
        } else {
            "$type was not expected to match $otherType but it did"
        }
        assertEquals(
            type.isMatching(otherType),
            expectedToMatch,
            assertionFailureMessage
        )
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

        @JvmStatic
        fun `simple BsonType match assertions`(): Array<Array<Any>> {
            val simpleBsonTypes = listOf(
                BsonDouble,
                BsonString,
                BsonBoolean,
                BsonDate,
                BsonInt32,
                BsonInt64,
                BsonDecimal128,
                BsonObjectId,
                BsonNull,
            )

            return simpleBsonTypes.flatMap { bsonType ->
                listOf(
                    // A type matches itself, Any and AnyOf
                    // (if the union contains the provided type)
                    arrayOf(bsonType, bsonType, true),
                    arrayOf(bsonType, BsonAny, true),
                    arrayOf(bsonType, BsonAnyOf(bsonType, simpleBsonTypes.subtract(setOf(bsonType)).random()), true),

                    // and does not match anything else
                    arrayOf(bsonType, simpleBsonTypes.subtract(setOf(bsonType)).random(), false),
                    // and does not match an AnyOf when the union does not contain the expected type
                    arrayOf(
                        bsonType,
                        BsonAnyOf(
                            simpleBsonTypes.subtract(setOf(bsonType)).random(),
                            simpleBsonTypes.subtract(setOf(bsonType)).random()
                        ),
                        false
                    ),
                )
            }.toTypedArray()
        }

        @JvmStatic
        fun `BsonAnyOf match assertions`(): Array<Array<Any>> =
            arrayOf(
                // matches other simple type if it is in union
                arrayOf(BsonAnyOf(BsonString, BsonNull), BsonString, true),
                arrayOf(BsonAnyOf(BsonDouble, BsonNull), BsonDouble, true),
                arrayOf(BsonAnyOf(BsonBoolean, BsonNull), BsonBoolean, true),
                arrayOf(BsonAnyOf(BsonInt32, BsonNull), BsonInt32, true),
                arrayOf(BsonAnyOf(BsonDate, BsonNull), BsonDate, true),
                // also matches null if it is in union
                arrayOf(BsonAnyOf(BsonDate, BsonNull), BsonNull, true),
                // also matches Any because the individual types themselves matches Any
                arrayOf(BsonAnyOf(BsonDate, BsonNull), BsonAny, true),

                // matches when the other type is AnyOf
                arrayOf(BsonAnyOf(BsonString, BsonNull), BsonAnyOf(BsonString, BsonNull), true),
                arrayOf(BsonAnyOf(BsonDouble, BsonNull), BsonAnyOf(BsonDouble, BsonNull), true),
                arrayOf(BsonAnyOf(BsonBoolean, BsonNull), BsonAnyOf(BsonBoolean, BsonNull), true),
                arrayOf(BsonAnyOf(BsonInt32, BsonNull), BsonAnyOf(BsonInt32, BsonNull), true),
                arrayOf(BsonAnyOf(BsonDate, BsonNull), BsonAnyOf(BsonDate, BsonNull), true),
                // also matches null if it is in union
                arrayOf(BsonAnyOf(BsonDate, BsonNull), BsonAnyOf(BsonNull), true),

                // should not match when the underlying union types do not match
                arrayOf(BsonAnyOf(BsonString, BsonNull), BsonInt32, false),
                arrayOf(BsonAnyOf(BsonInt32, BsonNull), BsonInt64, false),
                arrayOf(BsonAnyOf(BsonInt32, BsonNull), BsonAnyOf(BsonString, BsonDate), false),
                arrayOf(BsonAnyOf(BsonString, BsonDate), BsonNull, false),
            )

        @JvmStatic
        fun `BsonArray match assertions`(): Array<Array<Any>> =
            arrayOf(
                // Matches Array of simple types
                arrayOf(BsonArray(BsonString), BsonArray(BsonString), true),
                arrayOf(BsonArray(BsonDouble), BsonArray(BsonDouble), true),
                arrayOf(BsonArray(BsonBoolean), BsonArray(BsonBoolean), true),
                arrayOf(BsonArray(BsonInt32), BsonArray(BsonInt32), true),
                arrayOf(BsonArray(BsonDate), BsonArray(BsonDate), true),
                // Matches an Array of nulls
                arrayOf(BsonArray(BsonNull), BsonArray(BsonNull), true),
                // also matches Any
                arrayOf(BsonArray(BsonDate), BsonAny, true),
                // matches AnyOf when the array schema matches
                arrayOf(BsonArray(BsonString), BsonAnyOf(BsonArray(BsonString), BsonNull), true),
                arrayOf(BsonArray(BsonInt32), BsonAnyOf(BsonArray(BsonString), BsonArray(BsonInt32)), true),
                // matches when array elements are AnyOf
                arrayOf(BsonArray(BsonAnyOf(BsonString, BsonNull)), BsonArray(BsonString), true),
                arrayOf(BsonArray(BsonAnyOf(BsonString, BsonNull)), BsonArray(BsonAnyOf(BsonString, BsonDate)), true),

                // should not match otherwise
                arrayOf(BsonArray(BsonString), BsonArray(BsonDate), false),
                arrayOf(BsonArray(BsonDate), BsonArray(BsonNull), false),
                arrayOf(BsonArray(BsonDate), BsonNull, false),
                arrayOf(BsonArray(BsonDate), BsonAnyOf(BsonArray(BsonString)), false),
                arrayOf(BsonArray(BsonAnyOf(BsonString, BsonNull)), BsonArray(BsonAnyOf(BsonDate)), false),
            )

        @JvmStatic
        // ktlint complains about this method being too long but no real 
        // benefit in splitting this up as that would make reading test
        // cases difficult
        @Suppress("ktlint")
        fun `BsonObject match assertions`(): Array<Array<Any>> =
            arrayOf(
                // Matches when structurally similar
                arrayOf(
                    BsonObject(
                        mapOf(
                            "string" to BsonString,
                            "double" to BsonDouble,
                        )
                    ),
                    BsonObject(
                        mapOf(
                            "string" to BsonString,
                            "double" to BsonDouble,
                        )
                    ),
                    true
                ),
                arrayOf(
                    BsonObject(
                        mapOf(
                            "string" to BsonString,
                        )
                    ),
                    BsonObject(
                        mapOf(
                            "string" to BsonString,
                            "double" to BsonDouble,
                        )
                    ),
                    true
                ),
                arrayOf(
                    BsonObject(
                        mapOf(
                            "stringArray" to BsonArray(BsonString),
                            "double" to BsonDouble,
                        )
                    ),
                    BsonObject(
                        mapOf(
                            "stringArray" to BsonArray(BsonString),
                            "double" to BsonDouble,
                        )
                    ),
                    true
                ),
                arrayOf(
                    BsonObject(
                        mapOf(
                            "stringArray" to BsonArray(BsonString),
                            "double" to BsonDouble,
                        )
                    ),
                    BsonAny,
                    true
                ),
                arrayOf(
                    BsonObject(
                        mapOf(
                            "stringArray" to BsonArray(BsonString),
                        )
                    ),
                    BsonAnyOf(
                        BsonObject(
                            mapOf(
                                "stringArray" to BsonArray(BsonString),
                                "double" to BsonDouble,
                            )
                        ),
                        BsonNull
                    ),
                    true
                ),

                // does not match
                // not all keys in first are in second
                arrayOf(
                    BsonObject(
                        mapOf(
                            "string" to BsonString,
                            "double" to BsonDouble,
                        )
                    ),
                    BsonObject(
                        mapOf(
                            "string" to BsonString
                        )
                    ),
                    false
                ),
                // type mismatch
                arrayOf(
                    BsonObject(
                        mapOf(
                            "string" to BsonString,
                            "double" to BsonDouble,
                        )
                    ),
                    BsonObject(
                        mapOf(
                            "string" to BsonString,
                            "double" to BsonDecimal128,
                        )
                    ),
                    false
                ),
                // no part of AnyOf matches here
                arrayOf(
                    BsonObject(
                        mapOf(
                            "string" to BsonString,
                            "double" to BsonDouble,
                        )
                    ),
                    BsonAnyOf(
                        BsonObject(
                            mapOf(
                                "string" to BsonString,
                                "double" to BsonDecimal128,
                            )
                        ),
                        BsonObject(
                            mapOf(
                                "string" to BsonString,
                            )
                        )
                    ),
                    false
                ),
                // Arrays are not of same type
                arrayOf(
                    BsonObject(
                        mapOf(
                            "stringArray" to BsonArray(BsonString),
                        )
                    ),
                    BsonObject(
                        mapOf(
                            "stringArray" to BsonArray(BsonDouble),
                        )
                    ),
                    false
                )
            )

        data class ExampleClass(
            val field: String,
        )
}
}
