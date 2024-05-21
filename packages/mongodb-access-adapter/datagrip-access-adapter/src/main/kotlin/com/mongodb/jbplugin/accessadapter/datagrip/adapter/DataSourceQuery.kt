package com.mongodb.jbplugin.accessadapter.datagrip.adapter

import com.google.gson.Gson
import com.intellij.database.console.session.DatabaseSession
import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.reflect.KClass
import kotlin.time.Duration

class DataSourceQuery<T: Any>(
    private val project: Project,
    private val dataSource: LocalDataSource,
    private val result: KClass<T>
) {
    private val gson = Gson()

    suspend fun runQuery(queryString: String, timeout: Duration): List<T> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { callback ->
            val session = getSession()
            val hasFinished = AtomicBoolean(false)

            launch {
                val query = DataGripQueryAdapter(queryString, result.java, gson, session) {
                    if (!hasFinished.compareAndSet(false, true)) {
                        callback.resume(it)
                    }
                }

                session.messageBus.dataProducer.processRequest(query)

                delay(timeout)
                if (!hasFinished.compareAndSet(false, true)) {
                    callback.cancel(TimeoutException("Timeout running query '$queryString'"))
                }
            }
        }
    }

    private fun getSession(): DatabaseSession {
        val sessions = DatabaseSessionManager.getSessions(project, dataSource)
        if (sessions.isEmpty()) {
            return DatabaseSessionManager.openSession(project, dataSource, "mongodb")
        }

        return sessions[0]
    }
}