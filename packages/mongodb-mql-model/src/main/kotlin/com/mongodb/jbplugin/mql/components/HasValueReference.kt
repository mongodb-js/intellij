package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.ComputedBsonType
import com.mongodb.jbplugin.mql.HasChildren
import com.mongodb.jbplugin.mql.Node

data class HasValueReference<S>(
    val reference: ValueReference<S>,
) : HasChildren<S> {

    sealed interface ValueReference<S>

    /**
     * Encodes a possible ValueReference that cannot be classified as one of the remaining
     * ValueReference implementations because of us not having enough metadata about it.
     */
    data object Unknown : ValueReference<Any>

    /**
     * Encodes a ValueReference when the value is mentioned statically in the user code.
     * For Example:
     * ```
     * Filters.eq("year", 1994)
     * ```
     * ValueReference here is of type Constant
     */
    data class Constant<S>(
        val source: S,
        val value: Any?,
        val type: BsonType,
    ) : ValueReference<S>

    /**
     * Encodes a ValueReference when the value is not mentioned statically in the user code and
     * only implied using the method call.
     * For Example:
     * ```
     * Filters.exists("year")
     * ```
     * Here the implied value is true and hence the ValueReference will be Inferred.
     */
    data class Inferred<S>(
        val source: S,
        val value: Any?,
        val type: BsonType,
    ) : ValueReference<S>

    /**
     * Encodes a ValueReference when the value is mentioned as a variable in the user code and the
     * specific value can only be known at the runtime. For example:
     * ```
     * Filters.eq("year", yearFromFunctionArgs)
     * ```
     * Because the exact value of yearFromFunctionArgs is not known, the reference is Runtime
     */
    data class Runtime<S>(
        val source: S,
        val type: BsonType,
    ) : ValueReference<S>

    /**
     * Encodes a ValueReference when the value is computed on the server side. For example
     * for $group stages in an aggregation pipeline:
     * ```
     * Aggregates.group("$year") //-> Computed(Node(HasFieldReference(...)))
     * ```
     * The computedExpression can not be known, so we don't have a BsonType attached to it.
     *
     * Unless it can be inferred from the expression (not implemented), we will assume it's
     * BsonAny
     */
    data class Computed<S>(
        val source: S,
        val type: ComputedBsonType<S>,
    ) : ValueReference<S>

    override val children: List<Node<S>>
        get() = when (reference) {
            is Computed -> listOf(reference.type.expression)
            else -> emptyList()
        }
}
