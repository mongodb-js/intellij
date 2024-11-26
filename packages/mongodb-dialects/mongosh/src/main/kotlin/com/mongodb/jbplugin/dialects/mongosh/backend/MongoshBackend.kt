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
class MongoshBackend(
    private val context: Context = DefaultContext(),
    val prettyPrint: Boolean = false,
    private val paddingSpaces: Int = 2
) : Context by context {
    private val output: StringBuilder = StringBuilder()

    private var line: Int = 0
    private var column: Int = 0
    private var paddingScopes: Stack<Int> = Stack<Int>().apply {
        push(0)
    }

    fun emitDbAccess(): MongoshBackend {
        emitAsIs("db")
        return emitPropertyAccess()
    }

    fun emitDatabaseAccess(dbName: ContextValue): MongoshBackend {
        val nextPadding = column - 1 // align to the dot

        emitAsIs("getSiblingDB")
        emitFunctionCall(long = false, {
            emitContextValue(dbName)
        })

        if (prettyPrint) {
            paddingScopes.push(nextPadding)
            emitNewLine()
        }

        return emitPropertyAccess()
    }

    fun emitCollectionAccess(collName: ContextValue): MongoshBackend {
        emitAsIs("getCollection")
        emitFunctionCall(long = false, {
            emitContextValue(collName)
        })

        if (prettyPrint) {
            emitNewLine()
        }

        return emitPropertyAccess()
    }

    fun emitObjectStart(long: Boolean = false): MongoshBackend {
        val nextPadding = paddingScopes.peek() + paddingSpaces
        if (long && prettyPrint) {
            paddingScopes.push(nextPadding)
            emitAsIs("{")
            emitNewLine()
        } else {
            emitAsIs("{")
        }
        return this
    }

    fun emitObjectEnd(long: Boolean = false): MongoshBackend {
        if (long && prettyPrint) {
            paddingScopes.pop()
            emitNewLine()
        }

        emitAsIs("}")
        return this
    }

    fun emitArrayStart(long: Boolean = false): MongoshBackend {
        val nextPadding = paddingScopes.peek() + paddingSpaces
        if (long && prettyPrint) {
            paddingScopes.push(nextPadding)
            emitAsIs("[")
            emitNewLine()
        } else {
            emitAsIs("[")
        }
        return this
    }

    fun emitArrayEnd(long: Boolean = false): MongoshBackend {
        if (long && prettyPrint) {
            paddingScopes.pop()
            emitNewLine()
        }
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
        val preludeBackend = MongoshBackend(context, prettyPrint, paddingSpaces)
        preludeBackend.variableList().sortedBy { it.name }.forEach {
            preludeBackend.emitVariableDeclaration(it.name, defaultValueOfBsonType(it.type))
        }

        val prelude = preludeBackend.output.toString()
        return (prelude + "\n" + output.toString()).trim()
    }

    fun emitFunctionName(name: String): MongoshBackend = emitAsIs(name)

    fun emitFunctionCall(long: Boolean = false, vararg body: MongoshBackend.() -> MongoshBackend): MongoshBackend {
        emitAsIs("(")
        if (body.isNotEmpty()) {
            if (long && prettyPrint) {
                val nextDelta = column - (paddingSpaces / 2)
                paddingScopes.push(nextDelta)
                emitNewLine()
            }

            body[0].invoke(this)
            body.slice(1 until body.size).forEach {
                if (long && prettyPrint) {
                    emitNewLine()
                }
                emitAsIs(", ")
                it(this)
            }
        }

        if (long && prettyPrint) {
            paddingScopes.pop()
            emitNewLine()
        }

        emitAsIs(")")
        return this
    }

    fun emitPropertyAccess(): MongoshBackend {
        emitAsIs(".")
        return this
    }

    fun emitComment(comment: String): MongoshBackend {
        emitAsIs("/* $comment */")
        return this
    }

    fun emitContextValue(value: ContextValue): MongoshBackend {
        when (value) {
            is ContextValue.Constant -> emitPrimitive(value.value, false)
            is ContextValue.Variable -> emitAsIs(value.name)
        }

        return this
    }

    fun emitNewLine(): MongoshBackend {
        output.append("\n")
        emitAsIs(" ".repeat(paddingScopes.lastOrNull() ?: 0))

        line += 1
        column = 1

        return this
    }

    private fun emitAsIs(string: String, encode: Boolean = true): MongoshBackend {
        val stringToOutput = if (encode) Encode.forJavaScript(string) else string

        output.append(stringToOutput)
        column += stringToOutput.length
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
        emitAsIs(serializePrimitive(value, isPlaceholder), encode = false)
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
    is ComputedBsonType<*> -> defaultValueOfBsonType(type.baseType)
}
