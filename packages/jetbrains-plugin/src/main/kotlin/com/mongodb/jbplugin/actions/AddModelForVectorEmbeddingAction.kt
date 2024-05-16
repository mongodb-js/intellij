package com.mongodb.jbplugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchChildOnUi
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.FormBuilder
import com.mongodb.jbplugin.vector.VectorEmbeddingForSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
class AddModelForVectorEmbeddingActionService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    fun loadModel() = coroutineScope.launchChildOnUi {
            val loadModelDialog = AddModelDialogWrapper()
            val embeddingForSearch = project.getService(VectorEmbeddingForSearch::class.java)

            coroutineScope.launch {
                val available = embeddingForSearch.availableModelList()
                loadModelDialog.setLoadedModels(available)
            }

            if (!loadModelDialog.showAndGet()) {
                return@launchChildOnUi
            }

            coroutineScope.launch {
                embeddingForSearch.loadModel(loadModelDialog.model)
            }
        }
    }

class AddModelForVectorEmbeddingAction: AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.getService(AddModelForVectorEmbeddingActionService::class.java)?.loadModel()
    }
}

class AddModelDialogWrapper: DialogWrapper(true) {
    private val modelSelectorComponent = ComboBox<String>(emptyArray())
    val model: String get() = modelSelectorComponent.item

    fun setLoadedModels(models: List<String>) {
        modelSelectorComponent.model = DefaultComboBoxModel(models.toTypedArray())
        isOKActionEnabled = true
    }

    init {
        modelSelectorComponent.isEditable = true
        isOKActionEnabled = false
        isModal = true
        setOKButtonText("Load Model")
        isOKActionEnabled = false
        init()
    }

    override fun createCenterPanel(): JComponent? {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Model", modelSelectorComponent)
            .addSeparator()
            .addTooltip("The chosen model will be downloaded if it's not cached. It might take a while to see it available.")
            .panel
    }
}