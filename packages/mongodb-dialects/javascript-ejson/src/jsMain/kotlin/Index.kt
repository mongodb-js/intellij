@file:OptIn(ExperimentalJsExport::class)

import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.DialectParser
import com.mongodb.jbplugin.mql.ast.*
import com.mongodb.jbplugin.mql.schema.*
import kotlin.js.Json

@JsExport
object JavascriptEjsonDialect : Dialect<Json> {
    override val parser = JavascriptEjsonDialectParser
}

@JsExport
object JavascriptEjsonDialectParser : DialectParser<Json, Dialect<Json>> {
    private val keysFn = js("Object.keys") as (Json) -> Array<String>

    override fun canParse(source: Json): Boolean = true

    override fun attachment(source: Json): Json = source

    override fun parse(source: Json): Node<Json> = parseEJsonRecursively(source)

    private fun parseEJsonRecursively(source: Json): Node<Json> {
        val keys = keysFn(source)

        val children =
            keys
                .map {
                    Node(
                        source,
                        arrayOf(
                            Named("eq"),
                            HasFieldReference(HasFieldReference.Known(it)),
                            HasValueReference(HasValueReference.Constant(source[it]!!, inferTypeFromValue(source[it]))),
                        ),
                    )
                }.toTypedArray()

        return Node(source, arrayOf(HasChildren(children)))
    }

    private fun inferTypeFromValue(value: Any?): BsonType =
        when (value) {
            is String -> BsonString
            is Int -> BsonInt32
            is Float, Double -> BsonDouble
            else -> BsonNull
        }
}
