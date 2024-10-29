package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component

data class HasSourceDialect(val name: DialectName) : Component {
    enum class DialectName {
        JAVA_DRIVER,
        SPRING_CRITERIA,
        SPRING_QUERY
    }
}
