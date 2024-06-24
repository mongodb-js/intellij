@file:OptIn(DelicateCoroutinesApi::class, ExperimentalJsExport::class)

import com.mongodb.jbplugin.linting.rules.TypeCheckQuery
import com.mongodb.jbplugin.linting.rules.TypeCheckWarning
import com.mongodb.jbplugin.mql.schema.BsonDocument
import com.mongodb.jbplugin.mql.schema.BsonInt32
import com.mongodb.jbplugin.mql.schema.Collection
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.js.Json
import kotlin.js.Promise

@JsExport
fun sayHi() {
    console.log("Hello World!")
}

@JsExport
fun typeCheck(jsonString: String): Promise<Array<TypeCheckWarning<Json>>> {
    val json = JSON.parse<Json>(jsonString)
    val node = JavascriptEjsonDialect.parser.parse(json)
    val collection =
        Collection(
            BsonDocument(mapOf("myField" to BsonInt32)),
        )

    return GlobalScope.promise {
        TypeCheckQuery.apply(node, collection).toTypedArray()
    }
}
