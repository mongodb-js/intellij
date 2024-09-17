/**
 * Represents all supported Bson types. We are not using the ones defined in the driver as we need more information,
 * like nullability and composability (for example, a value that can be either int or bool).
 */

package com.mongodb.jbplugin.mql

import org.bson.types.Decimal128
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Represents any of the valid BSON types.
 */
sealed interface BsonType {
    /**
     * Checks whether the underlying type is assignable to the provided type
     * Example usage:
     * ```kt
     * val fieldType = BsonAnyOf(BsonInt32, BsonNull)
     * val valueType = BsonInt32
     * valueType.isAssignableTo(fieldType) // true
     *
     * // or
     * val valueType = BsonObject(mapOf("name" to BsonString))
     * val fieldType = BsonObject(mapOf("name" to BsonString, "version" to BsonString))
     * valueType.isAssignableTo(fieldType) // true because all the keys in the value type are also in field type
     *
     * // or
     * val valueType = BsonAnyOf(BsonInt32, BsonNull)
     * val fieldType = BsonInt32
     * valueType.isAssignableTo(fieldType) // false because the value can be BsonNull but field expects to be BsonInt32
     * ```
     *
     * @param otherType
     */
    fun isAssignableTo(otherType: BsonType): Boolean = when (this) {
        otherType -> true
        is BsonAny -> true
        else -> when (otherType) {
            is BsonAny -> true
            is BsonAnyOf -> otherType.types.any { this.isAssignableTo(it) }
            else -> false
        }
    }
}

/**
 * A double (64 bit floating point)
 */
data object BsonDouble : BsonType

/**
 * BSON String
 */
data object BsonString : BsonType

/**
 * Boolean
 */
data object BsonBoolean : BsonType

/**
 * Date
 */
data object BsonDate : BsonType

/**
 * 32-bit integer
 */

data object BsonInt32 : BsonType

/**
 * 64-bit integer
 */
data object BsonInt64 : BsonType

/**
 * Decimal128 (128 bit floating point)
 */
data object BsonDecimal128 : BsonType

/**
 * ObjectId
 */
data object BsonObjectId : BsonType

/**
 * null / non existing field
 */

data object BsonNull : BsonType

/**
 * Represents a map of key -> type.
 *
 * @property schema
 */
data class BsonObject(
    val schema: Map<String, BsonType>,
) : BsonType {
    override fun isAssignableTo(otherType: BsonType): Boolean = when (otherType) {
        is BsonAny -> true
        is BsonAnyOf -> otherType.types.any { this.isAssignableTo(it) }
        is BsonObject -> this.isAssignableToBsonObjectType(otherType)
        else -> false
    }

    private fun isAssignableToBsonObjectType(otherType: BsonObject): Boolean = this.schema.all { (key, bsonType) ->
            otherType.schema[key]?.let { bsonType.isAssignableTo(it) } ?: false
        }
}

/**
 * Represents the possible types that can be included in an array.
 *
 * @property schema
 */
data class BsonArray(
    val schema: BsonType,
) : BsonType {
    override fun isAssignableTo(otherType: BsonType): Boolean = when (otherType) {
        is BsonAny -> true
        is BsonAnyOf -> otherType.types.any { this.isAssignableTo(it) }
        is BsonArray -> this.schema.isAssignableTo(otherType.schema)
        else -> false
    }
}

/**
 * This is not a BSON type per se, but need a value for an unknown
 * bson type.
 */
data object BsonAny : BsonType

/**
 * This is not a BSON type per se, but a schema is dynamic and for a single
 * field we can have multiple types of values, so we will map this scenario
 * with the AnyOf type.
 *
 * @property types
 */
data class BsonAnyOf(
    val types: Set<BsonType>,
) : BsonType {
    constructor(vararg types: BsonType) : this(types.toSet())

    override fun isAssignableTo(otherType: BsonType): Boolean =
        this.types.all { it.isAssignableTo(otherType) }
}

/**
 * Returns the inferred BSON type of the current Java class, considering it's nullability.
 *
 * @param value
 */
fun <T> Class<T>?.toBsonType(value: T? = null): BsonType {
    return when (this) {
        null -> BsonNull
        Float::class.javaPrimitiveType -> BsonDouble
        Float::class.javaObjectType -> BsonAnyOf(BsonNull, BsonDouble)
        Double::class.javaPrimitiveType -> BsonDouble
        Double::class.javaObjectType -> BsonAnyOf(BsonNull, BsonDouble)
        Boolean::class.javaPrimitiveType -> BsonBoolean
        Boolean::class.javaObjectType -> BsonAnyOf(BsonNull, BsonBoolean)
        Short::class.javaPrimitiveType -> BsonInt32
        Short::class.javaObjectType -> BsonAnyOf(BsonNull, BsonInt32)
        Int::class.javaPrimitiveType -> BsonInt32
        Int::class.javaObjectType -> BsonAnyOf(BsonNull, BsonInt32)
        Long::class.javaPrimitiveType -> BsonInt64
        Long::class.javaObjectType -> BsonAnyOf(BsonNull, BsonInt64)
        CharSequence::class.java, String::class.java -> BsonAnyOf(BsonNull, BsonString)
        Date::class.java, Instant::class.java, LocalDate::class.java, LocalDateTime::class.java ->
            BsonAnyOf(BsonNull, BsonDate)

        BigInteger::class.java -> BsonAnyOf(BsonNull, BsonInt64)
        BigDecimal::class.java -> BsonAnyOf(BsonNull, BsonDecimal128)
        Decimal128::class.java -> BsonAnyOf(BsonNull, BsonDecimal128)
        else ->
            if (Collection::class.java.isAssignableFrom(this) || Array::class.java.isAssignableFrom(this)) {
                return BsonAnyOf(BsonNull, BsonArray(BsonAny)) // types are lost at runtime
            } else if (Map::class.java.isAssignableFrom(this)) {
                value?.let {
                    val fields =
                        Map::class.java.cast(value).entries.associate {
                            it.key.toString() to it.value?.javaClass.toBsonType(it.value)
                        }
                    return BsonAnyOf(BsonNull, BsonObject(fields))
                } ?: return BsonAnyOf(BsonNull, BsonAny)
            } else {
                val fields =
                    this.declaredFields.associate {
                        it.name to it.type.toBsonType()
                    }

                return BsonAnyOf(BsonNull, BsonObject(fields))
            }
    }
}
