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
                    // A type is assignable itself, Any and AnyOf
                    // (if the union contains only the underlying type)
                    arrayOf(bsonType, bsonType, true),
                    arrayOf(BsonAny, bsonType, true),
                    arrayOf(BsonAnyOf(bsonType), bsonType, true),

                    // and any other type cannot be assigned to this type
                    arrayOf(simpleBsonTypes.subtract(setOf(bsonType)).random(), bsonType, false),
                    // not assignable because not all the types in AnyOf is assignable to this type
                    arrayOf(BsonAnyOf(bsonType, simpleBsonTypes.subtract(setOf(bsonType)).random()), bsonType, false),
                    // even an AnyOf with BsonNull won't be assignable to this type unless of-course the otherType
                    // itself is BsonNull
                    arrayOf(BsonAnyOf(bsonType, BsonNull), bsonType, bsonType == BsonNull),

                    // collection types are not assignable to simple types
                    arrayOf(BsonArray(bsonType), bsonType, false)
                )
            }.toTypedArray()
        }

        @JvmStatic
        fun `BsonAnyOf match assertions`(): Array<Array<Any>> =
            arrayOf(
                // A simple type can be assigned to BsonAnyOf if the same type is in the underlying union of BsonAnyOf
                arrayOf(BsonString, BsonAnyOf(BsonString), true),
                arrayOf(BsonDouble, BsonAnyOf(BsonDouble), true),
                arrayOf(BsonBoolean, BsonAnyOf(BsonBoolean), true),
                arrayOf(BsonInt32, BsonAnyOf(BsonInt32), true),
                arrayOf(BsonDate, BsonAnyOf(BsonDate), true),
                arrayOf(BsonNull, BsonAnyOf(BsonNull), true),
                // A BsonAny can be assigned as well
                arrayOf(BsonAny, BsonAnyOf(BsonDate, BsonNull), true),

                // A BsonAnyOf of exact same union can be assigned
                arrayOf(BsonAnyOf(BsonString, BsonNull), BsonAnyOf(BsonString, BsonNull), true),
                arrayOf(BsonAnyOf(BsonDouble, BsonNull), BsonAnyOf(BsonDouble, BsonNull), true),
                arrayOf(BsonAnyOf(BsonBoolean, BsonNull), BsonAnyOf(BsonBoolean, BsonNull), true),
                arrayOf(BsonAnyOf(BsonInt32, BsonNull), BsonAnyOf(BsonInt32, BsonNull), true),
                arrayOf(BsonAnyOf(BsonDate, BsonNull), BsonAnyOf(BsonDate, BsonNull), true),

                // A BsonAnyOf of a subset of union of the other type can also be assigned
                arrayOf(BsonAnyOf(BsonString), BsonAnyOf(BsonString, BsonNull), true),
                arrayOf(BsonAnyOf(BsonDouble), BsonAnyOf(BsonDouble, BsonNull), true),
                arrayOf(BsonAnyOf(BsonBoolean), BsonAnyOf(BsonBoolean, BsonNull), true),
                arrayOf(BsonAnyOf(BsonInt32), BsonAnyOf(BsonInt32, BsonNull), true),
                arrayOf(BsonAnyOf(BsonDate), BsonAnyOf(BsonDate, BsonNull), true),
                arrayOf(BsonAnyOf(BsonNull), BsonAnyOf(BsonDate, BsonNull), true),
                arrayOf(
                    BsonAnyOf(BsonInt32, BsonInt64),
                    BsonAnyOf(BsonInt32, BsonInt64, BsonDecimal128, BsonNull),
                    true
                ),

                // cannot assign because the union is not a subset
                arrayOf(BsonAnyOf(BsonString, BsonNull), BsonAnyOf(BsonString), false),
                arrayOf(BsonAnyOf(BsonString, BsonNull), BsonAnyOf(BsonString, BsonDate), false),
            )

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
                // because BsonAnyOf has a BsonNull which can't be assigned to BsonArray
                arrayOf(BsonArray(BsonAnyOf(BsonString, BsonNull)), BsonArray(BsonString), false),
                arrayOf(BsonArray(BsonAnyOf(BsonString, BsonNull)), BsonArray(BsonAnyOf(BsonString, BsonNull)), true),

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
                arrayOf(BsonArray(BsonAnyOf(BsonDate)), BsonArray(BsonAnyOf(BsonString, BsonNull)), false),
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
                            "double" to BsonDecimal128,
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
