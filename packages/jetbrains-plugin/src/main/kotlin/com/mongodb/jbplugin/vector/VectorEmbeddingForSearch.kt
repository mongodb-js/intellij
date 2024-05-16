package com.mongodb.jbplugin.vector

import ai.djl.Application
import ai.djl.engine.Engine
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory
import ai.djl.huggingface.zoo.HfZooProvider
import ai.djl.inference.Predictor
import ai.djl.pytorch.engine.PtEngineProvider
import ai.djl.repository.zoo.Criteria
import ai.djl.training.util.ProgressBar
import ai.djl.util.Progress
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.squareup.wire.internal.toUnmodifiableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.PROJECT)
class VectorEmbeddingForSearch(
    private val project: Project?
) {
    private val allModelList: MutableList<String> = mutableListOf()
    private val isLoading: AtomicBoolean = AtomicBoolean(false)
    private val isLoaded: AtomicBoolean = AtomicBoolean(false)

    private val modelProgress: MutableSet<String> = mutableSetOf()
    private val predictors: MutableMap<String, Predictor<String, FloatArray>> = mutableMapOf()

    init {
        Engine.registerEngine(PtEngineProvider())
    }
    suspend fun loadModel(name: String) = withContext(Dispatchers.IO) {
        if (modelProgress.contains(name)) {
            return@withContext
        }

        modelProgress.add(name)

        val criteria =
            Criteria.builder()
                .setTypes(String::class.java, FloatArray::class.java)
                .optModelZoo(HfZooProvider().modelZoo)
                .optArtifactId(name)
                .optTranslatorFactory(TextEmbeddingTranslatorFactory())
                .optProgress(ModelDownloadingProgress(project, name))
                .build()


        val model = criteria.loadModel()
        predictors[name] = model.newPredictor()
    }

    suspend fun availableModelList(): List<String> = withContext(Dispatchers.IO) {
        if (isLoaded.get()) {
            return@withContext allModelList.toUnmodifiableList()
        }

        if (isLoading.compareAndSet(false, true)) {
            val loaders = HfZooProvider().modelZoo.modelLoaders
            val validLoaders = loaders.filter { it.application == Application.NLP.TEXT_EMBEDDING }.map {
                it.artifactId
            }

            allModelList.addAll(validLoaders)
            isLoading.set(false)
        } else {
            while (!isLoading.get()) {
                delay(100.milliseconds)
            }
        }

        allModelList.toUnmodifiableList()
    }

    fun downloadedModelList(): List<String> = predictors.keys.toList()

    fun appendEmbeddings(model: String, docs: List<Map<String, Any>>, embeddingField: String, textProvider: (Map<String, Any>) -> String): List<Map<String, Any>> {
        return docs.chunked(100).parallelStream().flatMap { chunk ->
            chunk.stream().map {
                it + (embeddingField to predictors[model]!!.predict(textProvider(it)))
            }
        }.sequential().toList()
    }

    fun embeddings(model: String, text: String): FloatArray {
        if (!predictors.containsKey(model)) {
            return FloatArray(0)
        }

        return predictors[model]!!.predict(text)
    }
}

internal class ModelDownloadingProgress(private val project: Project?, model: String): Progress {
    private val progressBar = ProgressBar()
    private var progress: Long = 0
    private var endProgress: Long = 0
    private val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup("com.mongodb.plugin.vs")
        .createNotification("Preparing model ${model}", "", NotificationType.INFORMATION)

    override fun reset(message: String?, max: Long, trailingMessage: String?) {
        endProgress = max
        progress = 0
        notification.subtitle = trailingMessage ?: message
        notification.setContent(message)
        notification.notify(project)
        progressBar.reset(message, max, trailingMessage)
    }

    override fun start(initialProgress: Long) {
        progressBar.start(initialProgress)
        notification.notify(project)
    }

    override fun end() {
        progressBar.end()
        notification.setContent("Finished downloading.")
        notification.notify(project)
    }

    override fun increment(increment: Long) {
        progressBar.increment(increment)
        progress = increment
        notification.setContent("Progress: ${((progress.toFloat() / endProgress.toFloat()) * 100).toInt()}")
    }

    override fun update(newProgress: Long, message: String?) {
        progressBar.update(newProgress, message)
        progress = newProgress
        notification.setContent("${message ?: notification.content}: ${((progress.toFloat() / endProgress.toFloat()) * 100).toInt()}")
    }

}