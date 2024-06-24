package com.mongodb.jbplugin.linting

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val Dispatchers.CPU: CoroutineDispatcher
    get() = Default
