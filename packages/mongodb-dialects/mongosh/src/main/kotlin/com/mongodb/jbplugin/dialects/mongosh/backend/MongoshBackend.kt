package com.mongodb.jbplugin.dialects.mongosh.backend

import org.owasp.encoder.Encode

class MongoshBackend(private val context: Context = DefaultContext()): Context by context {
    private val output: StringBuilder = StringBuilder()

    fun emitDbAccess(): MongoshBackend {
        return emitPropertyAccess {
            emitAsIs("db")
        }
    }

    fun emitDatabaseAccess(dbName: ContextValue): MongoshBackend {
        return emitPropertyAccess {
            emitAsIs("getSiblingDB")
            emitFunctionCall({
                emitContextValue(dbName)
            })
        }
    }

    fun emitCollectionAccess(collName: ContextValue): MongoshBackend {
        return emitPropertyAccess {
            emitAsIs("getCollection")
            emitFunctionCall({
                emitContextValue(collName)
            })
        }
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

    fun emitArrayItem(value: ContextValue): MongoshBackend {
        emitContextValue(value)
        emitAsIs(", ")
        return this
    }

    fun emitArrayEnd(): MongoshBackend {
        emitAsIs("]")
        return this
    }

    fun emitObjectKey(key: ContextValue): MongoshBackend {
        when (key) {
            is ContextValue.Variable -> emitAsIs("[${key.name}]")
            is ContextValue.Constant -> emitPrimitive(key.value)
        }
        emitAsIs(": ")
        return this
    }

    fun emitObjectValue(value: ContextValue): MongoshBackend {
        emitContextValue(value)
        emitAsIs(", ")
        return this
    }

    fun computeOutput(): String {
        val preludeBackend = MongoshBackend(context)
        preludeBackend.variableList().sortedBy { it.name }.forEach {
            preludeBackend.emitVariableDeclaration(it.name, it.placeholderValue)
        }

        val prelude = preludeBackend.output.toString()
        return (prelude + "\n" + output.toString()).trim()
    }

    fun emitFunctionName(name: String): MongoshBackend {
        return emitAsIs(name)
    }

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

    private fun emitPropertyAccess(left: MongoshBackend.() -> MongoshBackend): MongoshBackend {
        left()
        output.append(".")
        return this
    }

    private fun emitAsIs(string: String): MongoshBackend {
        output.append(Encode.forJavaScript(string))
        return this
    }

    private fun emitContextValue(value: ContextValue): MongoshBackend {
        when (value) {
            is ContextValue.Constant -> emitPrimitive(value.value)
            is ContextValue.Variable -> emitAsIs(value.name)
        }

        return this
    }

    private fun emitVariableDeclaration(name: String, value: Any): MongoshBackend {
        emitAsIs("var ")
        emitAsIs(name)
        emitAsIs(" = ")
        emitPrimitive(value)
        emitNewLine()
        return this
    }

    private fun emitPrimitive(value: Any): MongoshBackend {
        when (value) {
            is String -> output.append('"', Encode.forJavaScript(value), '"')
            else -> output.append(Encode.forJavaScript(value.toString()))
        }

        return this
    }

    private fun emitNewLine(nextPadding: Int = 0): MongoshBackend {
        output.append("\n")
        output.append(" ".repeat(nextPadding))
        return this
    }
}