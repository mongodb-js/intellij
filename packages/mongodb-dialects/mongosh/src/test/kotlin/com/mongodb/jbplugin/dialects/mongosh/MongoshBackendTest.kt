package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.dialects.mongosh.backend.DefaultContext
import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MongoshBackendTest {
    @Test
    fun `generates a valid find query`() {
        assertGeneratedJs(
            """
            db.getSiblingDB("myDb").getCollection("myColl").find({ "field": 1, })
""".trimIndent()
        ) {
            emitDbAccess()
            emitDatabaseAccess(registerConstant("myDb"))
            emitCollectionAccess(registerConstant("myColl"))
            emitFunctionName("find")
            emitFunctionCall({
                emitObjectStart()
                emitObjectKey(registerConstant("field"))
                emitObjectValue(registerConstant(1))
                emitObjectEnd()
            })
        }
    }

    @Test
    fun `generates a valid query with runtime parameters`() {
        assertGeneratedJs(
            """
            var myColl = ""
            var myDb = ""
            var myValue = ""

            db.getSiblingDB(myDb).getCollection(myColl).find({ "field": myValue, })
""".trimIndent()
        ) {
            emitDbAccess()
            emitDatabaseAccess(registerVariable("myDb", ""))
            emitCollectionAccess(registerVariable("myColl", ""))
            emitFunctionName("find")
            emitFunctionCall({
                emitObjectStart()
                emitObjectKey(registerConstant("field"))
                emitObjectValue(registerVariable("myValue", ""))
                emitObjectEnd()
            })
        }
    }

    @Test
    fun `generates a valid update query`() {
        assertGeneratedJs(
            """
            var myColl = ""
            var myDb = ""
            var myValue = ""

            db.getSiblingDB(myDb).getCollection(myColl).update({ "field": myValue, }, { "myUpdate": 1, })
""".trimIndent()
        ) {
            emitDbAccess()
            emitDatabaseAccess(registerVariable("myDb", ""))
            emitCollectionAccess(registerVariable("myColl", ""))
            emitFunctionName("update")
            emitFunctionCall({
                emitObjectStart()
                emitObjectKey(registerConstant("field"))
                emitObjectValue(registerVariable("myValue", ""))
                emitObjectEnd()
            }, {
                emitObjectStart()
                emitObjectKey(registerConstant("myUpdate"))
                emitObjectValue(registerConstant(1))
                emitObjectEnd()
            })
        }
    }
}

private fun assertGeneratedJs(@Language("js") js: String, script: MongoshBackend.() -> MongoshBackend) {
    val generated = script(MongoshBackend(DefaultContext())).computeOutput()
    assertEquals(js, generated)
}