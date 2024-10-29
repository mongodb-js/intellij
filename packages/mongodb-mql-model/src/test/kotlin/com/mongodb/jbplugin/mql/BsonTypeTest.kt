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

@Suppress("TOO_LONG_FUNCTION")
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
        "assignable to a non higher precision number, non collection, non arbitrary BsonType",
        "assignable to BsonInt64",
        "assignable to BsonDecimal128",
        "assignable to BsonNull",
        "assignable to BsonAny",
        "assignable to BsonAnyOf",
        "BsonArray match assertions",
        "BsonObject match assertions",
    )
    fun `should calculate correctly whether a type matches the other provided type`(
        // calling these value and field type just for clarity around usage
        valueType: BsonType,
        fieldType: BsonType,
        expectedToMatch: Boolean,
    ) {
        val assertionFailureMessage = if (expectedToMatch) {
            "$valueType was expected to be assignable to $fieldType but it was not!"
        } else {
            "$valueType was not expected to be assignable to $fieldType but it was!"
        }
        val result = valueType.isAssignableTo(fieldType)
        assertEquals(
            result,
            expectedToMatch,
            assertionFailureMessage
        )
    }

    companion object {
        private val simpleTypes = listOf(
            BsonString,
            BsonBoolean,
            BsonDate,
            BsonObjectId,
            BsonInt32,
            BsonInt64,
            BsonDouble,
            BsonDecimal128,
        )

        private fun List<BsonType>.listExcluding(type: BsonType): List<BsonType> = subtract(
            setOf(type)
        ).toList()

        private fun List<BsonType>.listExcluding(types: Set<BsonType>): List<BsonType> = subtract(
            types
        ).toList()

        private fun List<BsonType>.randomOtherThan(type: BsonType): BsonType = subtract(
            setOf(type)
        ).random()

        private fun List<BsonType>.randomOtherThan(types: Set<BsonType>): BsonType = subtract(
            types
        ).random()

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
        fun `assignable to a non higher precision number, non collection, non arbitrary BsonType`(): Array<Array<Any>> {
            // Special cases for assignability for numerical types down below
            val types = simpleTypes.listExcluding(setOf(BsonInt64, BsonDecimal128))
            return types.flatMap { simpleType ->
                listOf(
                    arrayOf(simpleType, simpleType, true),
                    arrayOf(BsonAny, simpleType, true),
                    arrayOf(BsonAnyOf(simpleType), simpleType, true),
                    // For queries written in Java, it is possible that the values are boxed which makes them nullable
                    // also. Such values should still be assignable to a detected non-nullable BsonType
                    arrayOf(BsonAnyOf(simpleType, BsonNull), simpleType, true),
                    // A list of simple type is assignable to the simple type in case of filters like
                    // $in. Example: { continent: { $in: ["Europe", "Asia"] } }
                    arrayOf(BsonArray(simpleType), simpleType, true),
                    arrayOf(BsonArray(BsonAnyOf(simpleType)), simpleType, true),

                    // Any other type is just not assignable to this BsonType
                    arrayOf(simpleTypes.randomOtherThan(simpleType), simpleType, false),
                    arrayOf(BsonAnyOf(simpleTypes.randomOtherThan(simpleType)), simpleType, false),
                    arrayOf(
                        BsonAnyOf(simpleTypes.randomOtherThan(simpleType), BsonNull),
                        simpleType,
                        false
                    ),

                    // A null is not assignable to a strict type
                    arrayOf(BsonNull, simpleType, false),

                    // Collections as well are not assignable to these simple types
                    arrayOf(BsonObject(mapOf("simpleTypeField" to simpleType)), simpleType, false),
                )
            }.toTypedArray()
        }

        @JvmStatic
        fun `assignable to BsonInt64`(): Array<Array<Any>> = arrayOf(
            arrayOf(BsonInt64, BsonInt64, true),
            // Lower precision number can also be assigned
            arrayOf(BsonInt32, BsonInt64, true),
            arrayOf(BsonAny, BsonInt64, true),
            arrayOf(BsonAnyOf(BsonInt64), BsonInt64, true),
            arrayOf(BsonAnyOf(BsonInt32), BsonInt64, true),
            // For queries written in Java, it is possible that the values are boxed which makes them nullable
            // also. Such values should still be assignable to a detected non-nullable BsonType
            arrayOf(BsonAnyOf(BsonInt64, BsonNull), BsonInt64, true),
            arrayOf(BsonAnyOf(BsonInt32, BsonNull), BsonInt64, true),
            // Possible when used in a $in Filter
            arrayOf(BsonArray(BsonInt64), BsonInt64, true),

            // Any other type is just not assignable to this BsonType
            arrayOf(simpleTypes.randomOtherThan(setOf(BsonInt32, BsonInt64)), BsonInt64, false),
            arrayOf(
                BsonAnyOf(simpleTypes.randomOtherThan(setOf(BsonInt32, BsonInt64))),
                BsonInt64,
                false
            ),
            arrayOf(
                BsonAnyOf(simpleTypes.randomOtherThan(setOf(BsonInt32, BsonInt64)), BsonNull),
                BsonInt64,
                false
            ),

            // A null is not assignable to a strict type
            arrayOf(BsonNull, BsonInt64, false),

            // Collections as well are not assignable to these simple types
            arrayOf(BsonObject(mapOf("simpleTypeField" to BsonInt64)), BsonInt64, false),
        )

        @JvmStatic
        fun `assignable to BsonDecimal128`(): Array<Array<Any>> = arrayOf(
            arrayOf(BsonDecimal128, BsonDecimal128, true),
            // Lower precision number can also be assigned
            arrayOf(BsonDouble, BsonDecimal128, true),
            arrayOf(BsonAny, BsonDecimal128, true),
            arrayOf(BsonAnyOf(BsonDecimal128), BsonDecimal128, true),
            arrayOf(BsonAnyOf(BsonDouble), BsonDecimal128, true),
            // For queries written in Java, it is possible that the values are boxed which makes them nullable
            // also. Such values should still be assignable to a detected non-nullable BsonType
            arrayOf(BsonAnyOf(BsonDecimal128, BsonNull), BsonDecimal128, true),
            arrayOf(BsonAnyOf(BsonDouble, BsonNull), BsonDecimal128, true),
            // Possible when used in a $in Filter
            arrayOf(BsonArray(BsonDecimal128), BsonDecimal128, true),

            // Any other type is just not assignable to this BsonType
            arrayOf(
                simpleTypes.randomOtherThan(setOf(BsonDouble, BsonDecimal128)),
                BsonDecimal128,
                false
            ),
            arrayOf(
                BsonAnyOf(simpleTypes.randomOtherThan(setOf(BsonDouble, BsonDecimal128))),
                BsonDecimal128,
                false
            ),
            arrayOf(
                BsonAnyOf(simpleTypes.randomOtherThan(setOf(BsonDouble, BsonDecimal128)), BsonNull),
                BsonDecimal128,
                false
            ),

            // A null is not assignable to a strict type
            arrayOf(BsonNull, BsonDecimal128, false),

            // Collections as well are not assignable to these simple types
            arrayOf(BsonObject(mapOf("simpleTypeField" to BsonDecimal128)), BsonDecimal128, false),
        )

        @JvmStatic
        fun `assignable to BsonNull`(): Array<Array<Any>> = arrayOf(
            arrayOf(BsonNull, BsonNull, true),
            arrayOf(BsonAny, BsonNull, true),
            arrayOf(BsonAnyOf(BsonNull), BsonNull, true),

            arrayOf(simpleTypes.random(), BsonNull, false),
            arrayOf(BsonAnyOf(simpleTypes.random()), BsonNull, false),
            arrayOf(BsonAnyOf(simpleTypes.random(), BsonNull), BsonNull, false),

            arrayOf(BsonArray(simpleTypes.random()), BsonNull, false),
            arrayOf(BsonObject(mapOf("simpleTypeField" to simpleTypes.random())), BsonNull, false),
        )

        @JvmStatic
        fun `assignable to BsonAny`(): Array<Array<Any>> = arrayOf(
            arrayOf(BsonAny, BsonAny, true),
            arrayOf(BsonNull, BsonAny, true),
            arrayOf(simpleTypes.random(), BsonAny, true),
            arrayOf(BsonAnyOf(simpleTypes.random()), BsonAny, true),
            arrayOf(BsonAnyOf(simpleTypes.random(), BsonNull), BsonAny, true),

            arrayOf(BsonArray(simpleTypes.random()), BsonAny, true),
            arrayOf(BsonObject(mapOf("simpleTypeField" to simpleTypes.random())), BsonAny, true),
        )

        @JvmStatic
        fun `assignable to BsonAnyOf`(): Array<Array<Any>> {
            val types = simpleTypes.listExcluding(setOf(BsonInt64, BsonDecimal128))
            val positiveAssertions = types.flatMap { simpleType ->
                listOf(
                    // Non-nullable AnyOf
                    arrayOf(simpleType, BsonAnyOf(simpleType), true),
                    arrayOf(BsonAny, BsonAnyOf(simpleType), true),
                    arrayOf(BsonAnyOf(simpleType), BsonAnyOf(simpleType), true),
                    arrayOf(BsonAnyOf(simpleType, BsonNull), BsonAnyOf(simpleType), true),
                    // collection types assignability
                    arrayOf(BsonArray(simpleType), BsonAnyOf(BsonArray(simpleType)), true),
                    arrayOf(
                        BsonObject(mapOf("simpleField" to simpleType)),
                        BsonAnyOf(BsonObject(mapOf("simpleField" to simpleType))),
                        true
                    ),

                    // Nullable AnyOf
                    arrayOf(simpleType, BsonAnyOf(simpleType, BsonNull), true),
                    arrayOf(BsonAny, BsonAnyOf(simpleType, BsonNull), true),
                    arrayOf(BsonNull, BsonAnyOf(simpleType, BsonNull), true),
                    arrayOf(BsonAnyOf(simpleType), BsonAnyOf(simpleType, BsonNull), true),
                    arrayOf(BsonAnyOf(simpleType, BsonNull), BsonAnyOf(simpleType, BsonNull), true),
                    // collection types assignability
                    arrayOf(BsonNull, BsonAnyOf(BsonArray(simpleType), BsonNull), true),
                    arrayOf(
                        BsonArray(simpleType),
                        BsonAnyOf(BsonArray(simpleType), BsonNull),
                        true
                    ),
                    arrayOf(
                        BsonNull,
                        BsonAnyOf(BsonObject(mapOf("simpleField" to simpleType)), BsonNull),
                        true
                    ),
                    arrayOf(
                        BsonObject(mapOf("simpleField" to simpleType)),
                        BsonAnyOf(BsonObject(mapOf("simpleField" to simpleType)), BsonNull),
                        true
                    ),
                )
            }

            val negativeAssertions = types.flatMap { simpleType ->
                listOf(
                    arrayOf(
                        simpleType,
                        BsonAnyOf(
                            simpleType,
                            simpleTypes.randomOtherThan(
                                setOf(simpleType, BsonInt64, BsonDecimal128)
                            )
                        ),
                        false
                    ),
                    arrayOf(simpleTypes.randomOtherThan(simpleType), BsonAnyOf(simpleType), false),
                    arrayOf(BsonNull, BsonAnyOf(simpleType), false),
                    arrayOf(
                        BsonAnyOf(simpleTypes.randomOtherThan(simpleType)),
                        BsonAnyOf(simpleType),
                        false
                    ),
                    arrayOf(
                        BsonAnyOf(simpleTypes.randomOtherThan(simpleType), BsonNull),
                        BsonAnyOf(simpleType),
                        false
                    ),
                    // collection types assignability
                    arrayOf(
                        BsonArray(simpleTypes.randomOtherThan(simpleType)),
                        BsonAnyOf(BsonArray(simpleType)),
                        false
                    ),
                    arrayOf(
                        BsonObject(mapOf("simpleField" to simpleTypes.randomOtherThan(simpleType))),
                        BsonAnyOf(BsonObject(mapOf("simpleField" to simpleType))),
                        false
                    ),

                    // Nullable AnyOf
                    arrayOf(
                        simpleTypes.randomOtherThan(simpleType),
                        BsonAnyOf(simpleType, BsonNull),
                        false
                    ),
                    arrayOf(
                        BsonAnyOf(simpleTypes.randomOtherThan(simpleType)),
                        BsonAnyOf(simpleType, BsonNull),
                        false
                    ),
                    arrayOf(
                        BsonAnyOf(simpleTypes.randomOtherThan(simpleType), BsonNull),
                        BsonAnyOf(simpleType, BsonNull),
                        false
                    ),
                )
            }

            return (positiveAssertions + negativeAssertions).toTypedArray()
        }

        @JvmStatic
        fun `BsonArray match assertions`(): Array<Array<Any>> =
            arrayOf(
                // Similar type arrays can be assigned to each other
                arrayOf(BsonArray(BsonString), BsonArray(BsonString), true),
                arrayOf(BsonArray(BsonDouble), BsonArray(BsonDouble), true),
                arrayOf(BsonArray(BsonBoolean), BsonArray(BsonBoolean), true),
                arrayOf(BsonArray(BsonInt32), BsonArray(BsonInt32), true),
                arrayOf(BsonArray(BsonDate), BsonArray(BsonDate), true),
                arrayOf(BsonArray(BsonNull), BsonArray(BsonNull), true),
                // BsonArray of BsonAnyOf type can be assigned when the underlying types of AnyOf matches that of the
                // other Array
                arrayOf(BsonArray(BsonAnyOf(BsonString)), BsonArray(BsonString), true),
                arrayOf(BsonArray(BsonAnyOf(BsonString, BsonNull)), BsonArray(BsonString), true),
                arrayOf(
                    BsonArray(BsonAnyOf(BsonString, BsonNull)),
                    BsonArray(BsonAnyOf(BsonString, BsonNull)),
                    true
                ),
                // This happens when the underlying field is array of simple type and we are querying
                // for documents having just the mentioned value of mentioned type
                // example: { list_of_amenities: "TV" }
                arrayOf(BsonString, BsonArray(BsonString), true),
                arrayOf(
                    BsonAnyOf(BsonString, BsonNull),
                    BsonArray(BsonAnyOf(BsonString, BsonNull)),
                    true
                ),

                // BsonAny can be assigned
                arrayOf(BsonAny, BsonArray(BsonDate), true),
                // BsonAnyOf can be assigned when the array schema on the right matches all the union types
                arrayOf(BsonAnyOf(BsonArray(BsonString)), BsonArray(BsonString), true),
                // otherwise not - in this case BsonNull cannot be assigned to BsonArray(BsonInt32)
                arrayOf(BsonAnyOf(BsonArray(BsonString), BsonNull), BsonArray(BsonInt32), false),

                // should not match otherwise
                arrayOf(BsonArray(BsonDate), BsonArray(BsonString), false),
                arrayOf(BsonArray(BsonNull), BsonArray(BsonDate), false),
                arrayOf(BsonNull, BsonArray(BsonDate), false),
                arrayOf(BsonAnyOf(BsonArray(BsonString)), BsonArray(BsonDate), false),
                arrayOf(
                    BsonArray(BsonAnyOf(BsonDate)),
                    BsonArray(BsonAnyOf(BsonString, BsonNull)),
                    false
                ),
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
                    BsonAny,
                    BsonObject(
                        mapOf(
                            "stringArray" to BsonArray(BsonString),
                            "double" to BsonDouble,
                        )
                    ),
                    true
                ),
                arrayOf(
                    BsonAnyOf(
                        BsonObject(
                            mapOf(
                                "stringArray" to BsonArray(BsonString),
                            )
                        ),
                    ),
                    BsonObject(
                        mapOf(
                            "stringArray" to BsonArray(BsonString),
                            "double" to BsonDouble,
                        )
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
                            "double" to BsonInt64,
                        )
                    ),
                    false
                ),
                // BsonAnyOf can't be assigned if there are more in union types other than the object type itself
                arrayOf(
                    BsonAnyOf(
                        // this is not assignable because of type mismatch
                        BsonObject(
                            mapOf(
                                "string" to BsonString,
                                "double" to BsonDecimal128,
                            )
                        ),
                        // this is assignable but the entire BsonAnyOf is still not because of the earlier type mismatch
                        BsonObject(
                            mapOf(
                                "string" to BsonString,
                            )
                        )
                    ),
                    BsonObject(
                        mapOf(
                            "string" to BsonString,
                            "double" to BsonDouble,
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
