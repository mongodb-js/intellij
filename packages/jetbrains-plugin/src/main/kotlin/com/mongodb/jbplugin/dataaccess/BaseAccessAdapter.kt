package com.mongodb.jbplugin.dataaccess

import com.google.gson.Gson
import com.intellij.database.console.session.DatabaseSession
import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.time.Duration

internal class BaseAccessAdapter(
    private val project: Project,
    private val dataSource: LocalDataSource
) {
    private val gson = Gson()

    suspend inline fun <reified T : Any> runQuery(queryString: String, timeout: Duration): List<T> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { callback ->
            val session = getSession()
            val hasFinished = AtomicBoolean(false)

            launch {
                val query = DataGripQueryAdapter(queryString, T::class.java, gson, session) {
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