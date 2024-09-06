/**
 * Represents a dialect, which is a way of writing MongoDb queries. Each dialect might have a
 * parser, that will convert the input content to an MQL AST. Dialects can also have a formatter
 * that define how a MQL AST is transformed to text parseable by the dialect.
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
 * @param C
 */
interface Dialect<S, C> {
    /**
     * The parser for queries. This is mandatory.
     */
    val parser: DialectParser<S>

    /**
     * The formatter for queries and types. This is mandatory.
     */
    val formatter: DialectFormatter

    /**
     * The context extractor. Only useful if we need additional information for the query, and it's
     * not part of the source code. Can be null otherwise.
     */
    val connectionContextExtractor: ConnectionContextExtractor<C>?

/**
     * Checks if the source S contains references to this dialect.
     *
     * @param source
     * @return
     */
    fun isUsableForSource(source: S): Boolean
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

    fun isReferenceToDatabase(source: S): Boolean

    fun isReferenceToCollection(source: S): Boolean

    fun isReferenceToField(source: S): Boolean
}

/**
 * A formatter gets an MQL element and can render it in a way that is useful
 * for a user given the Dialect.
 */
interface DialectFormatter {
    fun <S> formatQuery(query: Node<S>, explain: Boolean): String
    fun <S> indexCommandForQuery(query: Node<S>): String
    fun formatType(type: BsonType): String
}

/**
 * This class represents external context that is attached to a connection. For example,
 * in Spring Data, the database is specified outside the source code, in a properties or
 * yaml file.
 *
 * @property database
 */
data class ConnectionContext(
    val database: String?
)

/**
 * This enum specifies what requirements we have in terms of a context. So for example, Spring Data
 * lets you specify the database in an external file, so this would be a DATABASE requirement. The UI
 * will build the toolbar based on the set of requirements defined in the ConnectionContextExtractor.
 *
 * @see ConnectionContextExtractor
 * @see com.mongodb.jbplugin.editor.EditorToolbarDecorator
 */
enum class ConnectionContextRequirement {
    DATABASE
}

/**
 * This is an optional class that can be implemented to get connection information from outside the
 * source code where the code is.
 *
 * `C` is an opaque type that represents the Context holder (or the content root) of a dialect.
 * For example, for dialects that depend on IntelliJ, this will be the Project class.
 *
 * @see com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaContextExtractor
 *
 * @param C
 */
interface ConnectionContextExtractor<C> {
    fun requirements(): Set<ConnectionContextRequirement>
    fun gatherContext(contentRoot: C): ConnectionContext
}
