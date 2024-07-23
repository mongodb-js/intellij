/**
 * Represents a dialect, which is a way of writing MongoDb queries. Each dialect must have a
 * parser, that will convert the input content to an MQL AST.
 */

package com.mongodb.jbplugin.dialects

import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.Node

/**
 * Represents the dialect itself, S is the input type of the dialect. It's an opaque type,
 * we don't expect knowing anything about it. For any parser that depends on IntelliJ PsiElements,
 * S = PsiElement.
 *
 * @param S
 */
interface Dialect<S> {
    val parser: DialectParser<S>
    val formatter: DialectFormatter
}

/**
 * The parser itself. It only generates an MQL AST from the source, it doesn't analyse
 * anything.
 *
 * @param S
 */
interface DialectParser<S> {
    fun isCandidateForQuery(source: S): Boolean

    fun attachment(source: S): S

    fun parse(source: S): Node<S>
}

/**
 * A formatter gets an MQL element and can render it in a way that is useful
 * for a user given the Dialect.
 */
interface DialectFormatter {
    fun formatType(type: BsonType): String
}
