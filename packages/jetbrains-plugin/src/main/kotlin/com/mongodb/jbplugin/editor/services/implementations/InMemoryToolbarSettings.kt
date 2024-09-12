package com.mongodb.jbplugin.editor.services.implementations

import com.mongodb.jbplugin.editor.services.ToolbarSettings
import java.util.concurrent.atomic.AtomicReference

/**
 * @param initialDataSourceId
 * @param initialDatabase
 */
class InMemoryToolbarSettings(initialDataSourceId: String? = null, initialDatabase: String? = null) : ToolbarSettings {
    private val _dataSourceId = AtomicReference<String?>(initialDataSourceId)
    private val _database = AtomicReference<String?>(initialDatabase)
    override var dataSourceId: String?
        get() = _dataSourceId.get()
        set(value) {
            _dataSourceId.set(value)
        }
    override var database: String?
        get() = _database.get()
        set(value) {
            _dataSourceId.set(value)
        }
}