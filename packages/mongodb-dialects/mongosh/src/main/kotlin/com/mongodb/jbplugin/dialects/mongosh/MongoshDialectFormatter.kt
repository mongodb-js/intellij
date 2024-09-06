package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference

object MongoshDialectFormatter : DialectFormatter {
    override fun <S> formatQuery(query: Node<S>, explain: Boolean): String {
        val collectionRefComponent = query.component<HasCollectionReference<S>>() ?: return ""
        val collectionRef = collectionRefComponent.reference as? HasCollectionReference.Known<S> ?: return ""
        if (collectionRef.namespace == Namespace("production", "trips")) {
            return """
                db.getSiblingDb("production").getCollection("trips").find({ 
                    "disputes.status": "pending", 
                    "disputes.type": "fare" 
                })
            """.trimIndent()
        }

        return ""
    }
    override fun <S> indexCommandForQuery(query: Node<S>): String {
        val collectionRefComponent = query.component<HasCollectionReference<S>>() ?: return ""
        val collectionRef = collectionRefComponent.reference as? HasCollectionReference.Known<S> ?: return ""
        if (collectionRef.namespace == Namespace("production", "trips")) {
            return """
                // Potential fields to consider indexing: disputes.status, disputes.type 
                // Learn about creating an index: https://www.mongodb.com/docs/manual/core/data-model-operations/ 
                db.getSiblingDb("production").getCollection("trips").createIndex({ "<your_field>": 1 })
            """.trimIndent()
        }

        return ""
    }
    override fun formatType(type: BsonType) = ""
}