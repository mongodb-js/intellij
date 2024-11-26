package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component

class IsCommand(val type: CommandType) : Component {
    enum class CommandType(val canonical: String) {
        AGGREGATE("aggregate"),
        COUNT_DOCUMENTS("countDocuments"),
        DELETE_MANY("deleteMany"),
        DELETE_ONE("deleteOne"),
        DISTINCT("distinct"),
        ESTIMATED_DOCUMENT_COUNT("estimatedDocumentCount"),
        FIND_MANY("find"),
        FIND_ONE("findOne"),
        FIND_ONE_AND_DELETE("findOneAndDelete"),
        FIND_ONE_AND_REPLACE("findOneAndDelete"),
        FIND_ONE_AND_UPDATE("findOneAndUpdate"),
        INSERT_MANY("insertMany"),
        INSERT_ONE("insertOne"),
        REPLACE_ONE("replaceOne"),
        UPDATE_MANY("updateMany"),
        UPDATE_ONE("updateOne"),
        UPSERT("updateOne"), // this is update with upsert
        UNKNOWN("<unknown>")
    }
}
