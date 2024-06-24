@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package com.mongodb.jbplugin.mql.schema

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

class Cluster(
    val databases: Array<Database>,
)
