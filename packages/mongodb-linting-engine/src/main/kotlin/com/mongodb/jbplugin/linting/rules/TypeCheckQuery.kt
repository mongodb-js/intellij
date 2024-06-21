package com.mongodb.jbplugin.linting.rules

import com.mongodb.jbplugin.linting.CPU
import com.mongodb.jbplugin.mql.ast.HasChildren
import com.mongodb.jbplugin.mql.ast.HasFieldReference
import com.mongodb.jbplugin.mql.ast.HasValueReference
import com.mongodb.jbplugin.mql.ast.Node
import com.mongodb.jbplugin.mql.schema.BsonType
import com.mongodb.jbplugin.mql.schema.Collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface TypeCheckWarningInformation

data class NotCompatibleTypes(val codeType: BsonType, val schemaType: BsonType) : TypeCheckWarningInformation

data class TypeCheckWarning<S>(val node: Node<S>, val warning: TypeCheckWarningInformation)

object TypeCheckQuery {
    suspend fun <S> apply(
        node: Node<S>,
        collection: Collection,
    ): List<TypeCheckWarning<S>> =
        withContext(Dispatchers.CPU) {
            val hasChildren = node.component<HasChildren<S>>()
            if (hasChildren != null) {
                return@withContext hasChildren.children.flatMap { apply(it, collection) }
            }

            val hasField = node.component<HasFieldReference>()
            val hasValue = node.component<HasValueReference>()
            if (hasField != null && hasValue != null) {
                val fieldRef = hasField.reference
                if (fieldRef is HasFieldReference.Unknown) {
                    return@withContext emptyList()
                }

                val fieldName = (fieldRef as HasFieldReference.Known).fieldName
                val fieldType = collection.typeOf(fieldName)

                when (val valueRef = hasValue.reference) {
                    is HasValueReference.Constant -> {
                        if (valueRef.type != fieldType) {
                            return@withContext listOf(
                                TypeCheckWarning(node, NotCompatibleTypes(valueRef.type, fieldType)),
                            )
                        }
                    }
                    is HasValueReference.Runtime -> {
                        if (valueRef.type != fieldType) {
                            return@withContext listOf(
                                TypeCheckWarning(node, NotCompatibleTypes(valueRef.type, fieldType)),
                            )
                        }
                    }
                    else -> {}
                }
            }

            return@withContext emptyList()
        }
}
