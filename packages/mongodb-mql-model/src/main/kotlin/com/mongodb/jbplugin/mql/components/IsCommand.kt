package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component

class IsCommand(val type: CommandType) : Component {
    enum class CommandType {
        AGGREGATE,
        COUNT_DOCUMENTS,
        DELETE_MANY,
        DELETE_ONE,
        DISTINCT,
        ESTIMATED_DOCUMENT_COUNT,
        FIND_MANY,
        FIND_ONE,
        FIND_ONE_AND_DELETE,
        FIND_ONE_AND_REPLACE,
        FIND_ONE_AND_UPDATE,
        INSERT_MANY,
        INSERT_ONE,
        REPLACE_ONE,
        UPDATE_MANY,
        UPDATE_ONE,
        UNKNOWN
    }
}
