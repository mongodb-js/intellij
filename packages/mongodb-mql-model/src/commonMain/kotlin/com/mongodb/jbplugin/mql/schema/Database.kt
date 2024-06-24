@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package com.mongodb.jbplugin.mql.schema

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

class Database(
    val collection: Array<Collection>,
)
