package com.mongodb.jbplugin.actions

import com.google.gson.Gson
import com.intellij.ide.DataManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchChildOnUi
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.mongodb.jbplugin.vector.VectorEmbeddingForSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
class GenerateVectorEmbeddingActionService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    fun loadModel() = coroutineScope.launchChildOnUi {
        val embeddingForSearch = project.getService(VectorEmbeddingForSearch::class.java)
        val loadModelDialog = GenerateVectorEmbeddingDialogWrapper(embeddingForSearch.downloadedModelList())

        if (!loadModelDialog.showAndGet()) {
            return@launchChildOnUi
        }

        val vector = embeddingForSearch.embeddings(loadModelDialog.model, loadModelDialog.text)
        val vectorJs = Gson().toJson(vector)

        val editor: Editor? = DataManager.getInstance().dataContext.getData(PlatformDataKeys.EDITOR) as Editor?
        if (editor == null) {
            val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(vectorJs), null)
            NotificationGroupManager.getInstance()
                .getNotificationGroup("com.mongodb.plugin.vs")
                .createNotification("Vector copied to clipboard.", "", NotificationType.INFORMATION)
                .notify(project)
        } else {
            editor.document.insertString(0, vectorJs)
        }

        coroutineScope.launch {
            embeddingForSearch.loadModel(loadModelDialog.model)
        }
    }
}

class GenerateVectorEmbeddingAction: AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.getService(GenerateVectorEmbeddingActionService::class.java)?.loadModel()
    }
}

class GenerateVectorEmbeddingDialogWrapper(
    models: List<String>
): DialogWrapper(true) {
    private val modelSelectorComponent = ComboBox(models.toTypedArray())
    private val textToConvertComponent = JBTextField()

    val model: String get() = modelSelectorComponent.item
    val text: String get() = textToConvertComponent.text

    init {
        isModal = true
        setOKButtonText("Generate Vector")
        init()
    }

    override fun createCenterPanel(): JComponent? {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Model", modelSelectorComponent)
            .addTooltip("If your model is not in the dropdown, it might still be downloading.")
            .addLabeledComponent("Text", textToConvertComponent)
            .panel
    }
}