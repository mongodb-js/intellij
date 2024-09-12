package com.mongodb.jbplugin.editor.services

/**
 * Interface to work with data saved as part of Toolbar
 */
interface ToolbarSettings {
    // Implementations are supposed to define the getters and setters for this
    var dataSourceId: String?

    // Implementations are supposed to define the getters and setters for this
    var database: String?

    // A database can have an inferred value from the project context. Having a defined value to represent
    // an uninitialised state will help make decision on whether to use the inferred value or not
    companion object {
        const val UNINITIALIZED_DATABASE = "<<UNINITIALIZED_DATABASE>>"
    }
}