package com.mongodb.jbplugin.mql.ast

import com.mongodb.jbplugin.mql.schema.BsonType

interface Component

data class Node<S>(val source: S, val components: Array<Component>) {
    inline fun <reified C : Component> component(): C? {
        return components.find { it is C } as C?
    }
}

data class Named(val name: String) : Component

data class HasChildren<S>(val children: Array<Node<S>>) : Component

data class HasFieldReference(val reference: FieldReference) : Component {
    sealed interface FieldReference

    data class Known(val fieldName: String) : FieldReference

    data object Unknown : FieldReference
}

data class HasValueReference(val reference: ValueReference) : Component {
    sealed interface ValueReference

    data class Constant(val value: Any, val type: BsonType) : ValueReference

    data class Runtime(val type: BsonType) : ValueReference

    data object Unknown : ValueReference
}

data class UsesRegularIndex(val access: IndexAccess) : Component {
    sealed interface IndexAccess

    data object Lookup : IndexAccess

    data object Range : IndexAccess
}
