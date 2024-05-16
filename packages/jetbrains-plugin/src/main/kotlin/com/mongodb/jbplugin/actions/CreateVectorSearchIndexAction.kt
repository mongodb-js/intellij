package com.mongodb.jbplugin.actions

import com.google.gson.Gson
import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.DatabaseConnectionCore
import com.intellij.database.dataSource.localDataSource
import com.intellij.database.datagrid.DataRequest.RawRequest
import com.intellij.database.psi.DbDataSource
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchChildOnUi
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.mongodb.accessadapter.SearchIndexProvider
import com.mongodb.accessadapter.VectorSearchDefinition
import com.mongodb.accessadapter.atlas.AtlasSearchIndexProvider
import com.mongodb.jbplugin.settings.useSettings
import com.mongodb.jbplugin.vector.VectorEmbeddingForSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.swing.JComponent
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Service(Service.Level.PROJECT)
class CreateVectorSearchIndexActionService(
    private val coroutineScope: CoroutineScope
) {
    fun createSearchIndex(event: AnActionEvent) = coroutineScope.launchChildOnUi {
        val vectorEmbeddingForSearch = event.project!!.getService(VectorEmbeddingForSearch::class.java)
        val availableModels = vectorEmbeddingForSearch.downloadedModelList()
        val dataSource = event.dataContext.getData(PlatformDataKeys.PSI_ELEMENT) as DbDataSource

        val createVectorSearchDialogWrapper = CreateVectorSearchDialogWrapper(dataSource.name, availableModels)
        if (createVectorSearchDialogWrapper.showAndGet()) {
            coroutineScope.launch {
                val creatingSearchIndex = async {
                    createSearchIndexInAtlas(
                        dataSource,
                        createVectorSearchDialogWrapper
                    )
                }

                val migrateData = async {
                    migrateDataSourceInformation(
                        event.project!!,
                        dataSource,
                        createVectorSearchDialogWrapper
                    )
                }

                NotificationGroupManager.getInstance()
                    .getNotificationGroup("com.mongodb.plugin.vs")
                    .createNotification("The migration has started. It will take a few minutes.", NotificationType.INFORMATION)
                    .notify(event.project)

                creatingSearchIndex.await()
                migrateData.await()

                NotificationGroupManager.getInstance()
                    .getNotificationGroup("com.mongodb.plugin.vs")
                    .createNotification("The migration has finished.", NotificationType.INFORMATION)
                    .notify(event.project)
            }
        }
    }

    private suspend fun createSearchIndexInAtlas(
        dataSource: DbDataSource,
        dialogWrapper: CreateVectorSearchDialogWrapper
    ) = coroutineScope.launch {
        val settings = useSettings()
        val (publicKey, privateKey) = settings.atlasApiKey.split(":")
        val searchIndexProvider: SearchIndexProvider = AtlasSearchIndexProvider(publicKey, privateKey)
        val groupId = dataSource.comment!!

        searchIndexProvider.createVectorSearchIndex(
            groupId,
            dataSource.name,
            VectorSearchDefinition.newSingleField(
                dialogWrapper.database,
                dialogWrapper.collection,
                dialogWrapper.targetField,
                dialogWrapper.dimensions,
                dialogWrapper.similarityFunction
            )
        )
    }

    private suspend fun migrateDataSourceInformation(
        project: Project,
        dataSource: DbDataSource,
        dialogWrapper: CreateVectorSearchDialogWrapper
    ) = coroutineScope.launch {
        val vectorEmbeddingForSearch = project.getService(VectorEmbeddingForSearch::class.java)
        val ( session ) = DatabaseSessionManager.getSessions(project, dataSource.localDataSource!!)

        val docsToMigrate: List<Map<String, Any>> = suspendCoroutine { callback ->
            session.messageBus.dataProducer.processRequest(LoadAllDocumentsRawRequest(session, dialogWrapper, callback))
        }

        val newDocs = vectorEmbeddingForSearch.appendEmbeddings(
            dialogWrapper.model,
            docsToMigrate,
            dialogWrapper.targetField,
        ) {
            it[dialogWrapper.sourceField].toString()
        }

        newDocs.chunked(100).forEach { chunk ->
            suspendCoroutine { callback ->
                session.messageBus.dataProducer.processRequest(UpdateDocumentChunkRawRequest(session, dialogWrapper, chunk, callback))
            }
        }
    }
}

class LoadAllDocumentsRawRequest(
    owner: OwnerEx,
    private val dialogWrapper: CreateVectorSearchDialogWrapper,
    private val callback: Continuation<List<Map<String, Any>>>
): RawRequest(owner) {
    override fun processRaw(p0: Context?, p1: DatabaseConnectionCore?) {
        val remoteConnection = p1!!.remoteConnection
        val query = "db.getSiblingDB(\"${dialogWrapper.database}\").${dialogWrapper.collection}.find({}, { _id: 1, ${dialogWrapper.sourceField}: 1 }).limit(${dialogWrapper.limit})"
        val statement = remoteConnection.prepareStatement(query.trimIndent())

        val result: MutableList<Map<String, Any>> = mutableListOf()
        val resultSet = statement.executeQuery()
        while (resultSet.next()) {
            val hashMap = resultSet.getObject(1) as HashMap<String, Any>
            result.add(hashMap)
        }

        callback.resume(result)
    }
}

class UpdateDocumentChunkRawRequest(
    owner: OwnerEx,
    private val dialogWrapper: CreateVectorSearchDialogWrapper,
    private val chunk: List<Map<String, Any>>,
    private val callback: Continuation<Unit>
): RawRequest(owner) {
    override fun processRaw(p0: Context?, p1: DatabaseConnectionCore?) {
        val gson = Gson()
        val query = chunk.joinToString("\n") {
            "db.getSiblingDB(\"${dialogWrapper.database}\").${dialogWrapper.collection}.updateOne({ _id: ObjectId(\"${it["_id"]}\") }, { \$set: { ${dialogWrapper.targetField}: ${gson.toJson(it[dialogWrapper.targetField])} } })"
        }

        val remoteConnection = p1!!.remoteConnection
        val statement = remoteConnection.prepareStatement(query)

        println("RUNNING QUERY")
        statement.executeQuery()
        callback.resume(Unit)
    }
}

class CreateVectorSearchIndexAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.project!!.getService(CreateVectorSearchIndexActionService::class.java).createSearchIndex(event)
    }
}

class CreateVectorSearchDialogWrapper(
    dataSourceName: String,
    availableModels: List<String>
): DialogWrapper(true) {
    private val dataSourceNameComponent = JBTextField(dataSourceName).apply {
        isEditable = false
    }

    private val databaseComponent = JBTextField("")
    private val collectionComponent = JBTextField("")
    private val modelSelectorComponent = ComboBox(availableModels.toTypedArray())
    private val dimensionsComponent = JBTextField("")
    private val limitComponent = JBTextField("10")
    private val sourceFieldNameComponent = JBTextField("")
    private val targetFieldNameComponent = JBTextField("")
    private val similarityFunctionComponent = ComboBox(arrayOf("cosine"))

    val database: String get() = databaseComponent.text
    val collection: String get() = collectionComponent.text
    val model: String get() = modelSelectorComponent.item
    val dimensions: Int get() = dimensionsComponent.text.toInt()
    val limit: Int get() = limitComponent.text.toInt()
    val sourceField: String get() = sourceFieldNameComponent.text
    val targetField: String get() = targetFieldNameComponent.text
    val similarityFunction: String get() = similarityFunctionComponent.item

    init {
        isModal = true
        setOKButtonText("Create Index")
        init()
    }

    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Data source", dataSourceNameComponent)
            .addLabeledComponent("Database", databaseComponent)
            .addLabeledComponent("Collection", collectionComponent)
            .addSeparator()
            .addLabeledComponent("Source field", sourceFieldNameComponent)
            .addLabeledComponent("Model", modelSelectorComponent)
            .addTooltip("This model will be applied to all documents in the collection and store the results in the target field.")
            .addLabeledComponent("Target field", targetFieldNameComponent)
            .addLabeledComponent("Dimensions", dimensionsComponent)
            .addLabeledComponent("Similarity", similarityFunctionComponent)
            .addSeparator()
            .addLabeledComponent("Migration limit", limitComponent)
            .addTooltip("Amount of documents to migrate.")
            .panel
    }
}

