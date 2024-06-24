package com.mongodb.jbplugin.linting

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

actual val Dispatchers.CPU: CoroutineDispatcher
    get() = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
