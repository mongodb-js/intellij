package com.mongodb.jbplugin.dialects.mongosh.backend

import com.mongodb.jbplugin.mql.*
import org.bson.types.ObjectId
import org.owasp.encoder.Encode
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

private const val MONGODB_FIRST_RELEASE = "2009-02-11T18:00:00.000Z"

/**
 * @param context
 */
class MongoshBackend(private val context: Context = DefaultContext()) : Context by context {
    private val output: StringBuilder = StringBuilder()

    fun emitDbAccess(): MongoshBackend {
        emitAsIs("db")
        return emitPropertyAccess()
    }

    fun emitDatabaseAccess(dbName: ContextValue): MongoshBackend {
        emitAsIs("getSiblingDB")
        emitFunctionCall({
            emitContextValue(dbName)
        })
        return emitPropertyAccess()
    }

    fun emitCollectionAccess(collName: ContextValue): MongoshBackend {
        emitAsIs("getCollection")
        emitFunctionCall({
            emitContextValue(collName)
        })

        return emitPropertyAccess()
    }

    fun emitObjectStart(): MongoshBackend {
        emitAsIs("{ ")
        return this
    }

    fun emitObjectEnd(): MongoshBackend {
        emitAsIs("}")
        return this
    }

    fun emitArrayStart(): MongoshBackend {
        emitAsIs("[ ")
        return this
    }

    fun emitArrayEnd(): MongoshBackend {
        emitAsIs("]")
        return this
    }

    fun emitObjectKey(key: ContextValue): MongoshBackend {
        when (key) {
            is ContextValue.Variable -> emitAsIs("[${key.name}]")
            is ContextValue.Constant -> emitPrimitive(key.value, false)
        }
        emitAsIs(": ")
        return this
    }

    fun emitObjectValueEnd(): MongoshBackend {
        emitAsIs(", ")
        return this
    }

    fun computeOutput(): String {
        val preludeBackend = MongoshBackend(context)
        preludeBackend.variableList().sortedBy { it.name }.forEach {
            preludeBackend.emitVariableDeclaration(it.name, defaultValueOfBsonType(it.type))
        }

        val prelude = preludeBackend.output.toString()
        return (prelude + "\n" + output.toString()).trim()
    }

    fun emitFunctionName(name: String): MongoshBackend = emitAsIs(name)

    fun emitFunctionCall(vararg body: MongoshBackend.() -> MongoshBackend): MongoshBackend {
        output.append("(")
        if (body.isNotEmpty()) {
            body[0].invoke(this)
            body.slice(1 until body.size).forEach {
                output.append(", ")
                it(this)
            }
        }
        output.append(")")
        return this
    }

    fun emitPropertyAccess(): MongoshBackend {
        output.append(".")
        return this
    }

    fun emitComment(comment: String): MongoshBackend {
        output.append("/* $comment */")
        return this
    }

    fun emitContextValue(value: ContextValue): MongoshBackend {
        when (value) {
            is ContextValue.Constant -> emitPrimitive(value.value, false)
            is ContextValue.Variable -> emitAsIs(value.name)
        }

        return this
    }

    private fun emitAsIs(string: String): MongoshBackend {
        output.append(Encode.forJavaScript(string))
        return this
    }

    private fun emitVariableDeclaration(name: String, value: Any?): MongoshBackend {
        emitAsIs("var ")
        emitAsIs(name)
        emitAsIs(" = ")
        emitPrimitive(value, true)
        emitNewLine()
        return this
    }

    private fun emitPrimitive(value: Any?, isPlaceholder: Boolean): MongoshBackend {
        output.append(serializePrimitive(value, isPlaceholder))
        return this
    }

    private fun emitNewLine(nextPadding: Int = 0): MongoshBackend {
        output.append("\n")
        output.append(" ".repeat(nextPadding))
        return this
    }
}

private fun serializePrimitive(value: Any?, isPlaceholder: Boolean): String = when (value) {
    is Byte, Short, Int, Long, Float, Double -> Encode.forJavaScript(value.toString())
    is BigInteger -> "Decimal128(\"$value\")"
    is BigDecimal -> "Decimal128(\"$value\")"
    is Boolean -> value.toString()
    is ObjectId -> "ObjectId(\"${Encode.forJavaScript(value.toHexString())}\")"
    is Number -> Encode.forJavaScript(value.toString())
    is String -> '"' + Encode.forJavaScript(value) + '"'
    is Date, is Instant, is LocalDate, is LocalDateTime -> if (isPlaceholder) {
        "ISODate(\"$MONGODB_FIRST_RELEASE\")"
    } else {
        "ISODate()"
    }

    is Collection<*> -> value.joinToString(separator = ", ", prefix = "[", postfix = "]") {
        serializePrimitive(it, isPlaceholder)
    }

    is Map<*, *> -> value.entries.joinToString(separator = ", ", prefix = "{", postfix = "}") {
        "\"${it.key}\": ${serializePrimitive(it.value, isPlaceholder)}"
    }

    null -> "null"
    else -> "{}"
}

private fun defaultValueOfBsonType(type: BsonType): Any? = when (type) {
    BsonAny -> "any"
    is BsonAnyOf -> defaultValueOfBsonType(type.types.firstOrNull { it !is BsonNull } ?: BsonAny)
    is BsonArray -> emptyList<Any>()
    BsonBoolean -> false
    BsonDate -> Date.from(Instant.parse(MONGODB_FIRST_RELEASE))
    BsonDecimal128 -> BigInteger.ZERO
    BsonDouble -> 0.0
    BsonInt32 -> 0
    BsonInt64 -> 0
    BsonNull -> null
    is BsonObject -> emptyMap<Any, Any>()
    BsonObjectId -> ObjectId("000000000000000000000000")
    BsonString -> ""
}
