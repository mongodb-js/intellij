package com.mongodb.jbplugin.linting

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

expect val Dispatchers.CPU: CoroutineDispatcher
