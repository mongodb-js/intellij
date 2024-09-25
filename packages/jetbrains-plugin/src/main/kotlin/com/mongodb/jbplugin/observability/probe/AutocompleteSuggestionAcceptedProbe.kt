package com.mongodb.jbplugin.observability.probe

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.observability.LogMessage
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.hours

private val logger: Logger = logger<AutocompleteSuggestionAcceptedProbe>()

/**
 * This probe is emitted when an autocomplete suggestion is emitted. However, events are aggregated
 * and sent hourly to Segment.
 *
 * @param cs
 */
@Service
class AutocompleteSuggestionAcceptedProbe(
    cs: CoroutineScope,
) : AppLifecycleListener {
    private val telemetryJob: Job
    private val events: CopyOnWriteArrayList<SuggestionEvent>

    init {
        ApplicationManager
            .getApplication()
            .messageBus
            .connect()
            .subscribe(AppLifecycleListener.TOPIC, this)

        telemetryJob =
            cs.launch {
                telemetryLoop()
            }

        events = CopyOnWriteArrayList()
    }

    override fun appWillBeClosed(isRestart: Boolean) {
        telemetryJob.cancel()
        sendEvents()
    }

    fun databaseCompletionAccepted(dialect: Dialect<*, *>) {
        events.add(SuggestionEvent(dialect, SuggestionEvent.SuggestionEventType.DATABASE))
    }

    fun collectionCompletionAccepted(dialect: Dialect<*, *>) {
        events.add(SuggestionEvent(dialect, SuggestionEvent.SuggestionEventType.COLLECTION))
    }

    fun fieldCompletionAccepted(dialect: Dialect<*, *>) {
        events.add(SuggestionEvent(dialect, SuggestionEvent.SuggestionEventType.FIELD))
    }

    private suspend fun telemetryLoop(): Unit =
        withContext(Dispatchers.IO) {
            while (true) {
                // if it fails, ignore, we will retry in one hour
                runCatching {
                    sendEvents()
                }
                delay(1.hours)
            }
        }

    internal fun sendEvents() {
        val listCopy = events.toList()
        events.clear()

        val application = ApplicationManager.getApplication()
        val telemetry = application.getService(TelemetryService::class.java)
        val logMessage = application.getService(LogMessage::class.java)

        listCopy
            .groupingBy {
                Pair(it.dialect, it.type)
            }
            .eachCount()
            .map {
                TelemetryEvent.AutocompleteGroupEvent(
                    it.key.first,
                    it.key.second.publicName,
                    it.value
                )
            }
            .sortedBy { it.name }
            .forEach {
                telemetry.sendEvent(it)

                logger.info(
                    logMessage
                        .message("Autocomplete suggestion aggregated.")
                        .mergeTelemetryEventProperties(it)
                        .build(),
                )
            }
    }

    /**
     * @property dialect
     * @property type
     */
    private data class SuggestionEvent(
        val dialect: Dialect<*, *>,
        val type: SuggestionEventType,
    ) {
        /**
         * @property publicName
         */
        enum class SuggestionEventType(
            val publicName: String,
        ) {
            DATABASE("database"),
            COLLECTION("collection"),
            FIELD("field"),
        }
    }
}
