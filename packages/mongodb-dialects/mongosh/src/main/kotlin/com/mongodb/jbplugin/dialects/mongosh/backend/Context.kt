/**
 * Stores metadata information of what variables and constants are referenced
 * in generated output for the mongosh.
 */

package com.mongodb.jbplugin.dialects.mongosh.backend

import com.mongodb.jbplugin.mql.BsonType

/**
 * Types of values referenced.
 */
sealed interface ContextValue {
    /**
 * @property name
 * @property type
 */
data class Variable(val name: String, val type: BsonType) : ContextValue

/**
 * @property value
 */
data class Constant(val value: Any?) : ContextValue
}

/**
 * The context holds the list of variables and constants in a generated
 * source code.
 */
interface Context {
    fun variableList(): List<ContextValue.Variable>

    fun registerVariable(name: String, type: BsonType): ContextValue.Variable
    fun registerConstant(value: Any?): ContextValue.Constant
}

/**
 * Default context implementation that allows to add variables and constants, and keep tracks of duplicates.
 */
class DefaultContext : Context {
    private val endsWithNumber = Regex("[0-9]+$")
    private val variables = mutableMapOf<String, ContextValue.Variable>()
    private val counters = mutableMapOf<String, Int>()

    override fun variableList(): List<ContextValue.Variable> = variables.values.toList()

    override fun registerVariable(name: String, type: BsonType): ContextValue.Variable {
        val cleanName = name.replace(".", "_")

        if (variables.containsKey(cleanName)) {
// already exists, generate a new name
            val nameWithoutCounter = if (cleanName.matches(endsWithNumber)) {
                cleanName.replace(endsWithNumber, "")
            } else {
                cleanName
            }

            counters[nameWithoutCounter] = counters.getOrDefault(nameWithoutCounter, 0) + 1
            val nameWithCounter = cleanName + counters[nameWithoutCounter]
            val variable = ContextValue.Variable(nameWithCounter, type)
            variables[nameWithCounter] = variable
            return variable
        } else {
            val variable = ContextValue.Variable(cleanName, type)
            variables[cleanName] = variable
            return variable
        }
    }

    override fun registerConstant(value: Any?): ContextValue.Constant = ContextValue.Constant(value)
}