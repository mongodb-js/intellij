package com.mongodb.jbplugin.dialects.mongosh.backend

sealed interface ContextValue {
    data class Variable(val name: String, val placeholderValue: Any): ContextValue
    data class Constant(val value: Any): ContextValue
}

interface Context {
    fun variableList(): List<ContextValue.Variable>

    fun registerVariable(name: String, placeholderValue: Any): ContextValue.Variable
    fun registerConstant(value: Any): ContextValue.Constant
}

class DefaultContext : Context {
    private val endsWithNumber = Regex("[0-9]+$")
    private val variables = mutableMapOf<String, ContextValue.Variable>()
    private val counters = mutableMapOf<String, Int>()

    override fun variableList(): List<ContextValue.Variable> {
        return variables.values.toList()
    }

    override fun registerVariable(name: String, placeholderValue: Any): ContextValue.Variable {
        if (variables.containsKey(name)) { // already exists, generate a new name
            val nameWithoutCounter = if (name.matches(endsWithNumber)) {
                name.replace(endsWithNumber, "")
            } else {
                name
            }

            counters[nameWithoutCounter] = counters.getOrDefault(nameWithoutCounter, 0) + 1
            val nameWithCounter = name + counters[nameWithoutCounter]
            val variable = ContextValue.Variable(nameWithCounter, placeholderValue)
            variables[nameWithCounter] = variable
            return variable
        } else {
            val variable = ContextValue.Variable(name, placeholderValue)
            variables[name] = variable
            return variable
        }
    }

    override fun registerConstant(value: Any): ContextValue.Constant {
        return ContextValue.Constant(value)
    }
}