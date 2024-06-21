package com.mongodb.jbplugin.linting

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

val Dispatchers.CPU: ExecutorCoroutineDispatcher
    get() = Executors.newCachedThreadPool().asCoroutineDispatcher()
