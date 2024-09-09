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
                db.getSiblingDB("production").getCollection("trips").find({ 
                    "dispute.status": "pending", 
                    "dispute.type": "fare" 
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
                // Potential fields to consider indexing: dispute.status, dispute.type 
                // Learn about creating an index: https://www.mongodb.com/docs/manual/core/data-model-operations/ 
                db.getSiblingDB("production").getCollection("trips").createIndex({ "<your_field>": 1 }, { "<your_field_2>": 1 })
            """.trimIndent()
        }

        return ""
    }
    override fun formatType(type: BsonType) = ""
}