package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component

/**
 * @property name
 */
data class Named(
    val name: String,
) : Component
