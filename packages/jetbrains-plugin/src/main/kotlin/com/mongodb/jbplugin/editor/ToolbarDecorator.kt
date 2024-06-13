package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.model.RawDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.psi.*
import com.intellij.util.messages.MessageBusConnection
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JPanel

private class Toolbar(dataSources: List<LocalDataSource>) {
    val panel: JPanel = JPanel(BorderLayout())
    val connectionComboBox =
        ComboBox(
            DefaultComboBoxModel(
                dataSources.toTypedArray(),
            ),
        )

    init {
        panel.add(connectionComboBox, BorderLayout.EAST)
    }

    fun refreshDataSources(dataSources: List<LocalDataSource>) {
        val model = connectionComboBox.model as DefaultComboBoxModel<LocalDataSource>
        model.removeAllElements()
        model.addAll(dataSources)
    }
}

class ToolbarDecorator : EditorFactoryListener, DataSourceManager.Listener, PsiTreeChangeListener {
    private val toolbar = Toolbar(emptyList())
    private lateinit var connection: MessageBusConnection
    private lateinit var editor: Editor

    override fun editorCreated(event: EditorFactoryEvent) {
        editor = event.editor

        if (editor.project != null) {
            connection =
                editor.project!!
                    .messageBus
                    .connect()

            PsiManager.getInstance(editor.project!!).addPsiTreeChangeListener(this, connection)

            connection.subscribe(
                DataSourceManager.TOPIC,
                this,
            )

            ensureToolbarIsVisibleIfNecessary()
        }
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        Disposer.dispose(connection)
    }

    override fun <T : RawDataSource?> dataSourceAdded(
        manager: DataSourceManager<T>,
        dataSource: T & Any,
    ) {
        if (dataSource is LocalDataSource) {
            ensureToolbarIsVisibleIfNecessary(manager?.dataSources as List<LocalDataSource> ?: emptyList())
        }
    }

    override fun <T : RawDataSource?> dataSourceRemoved(
        manager: DataSourceManager<T>,
        dataSource: T & Any,
    ) {
        if (dataSource is LocalDataSource) {
            ensureToolbarIsVisibleIfNecessary(manager?.dataSources as List<LocalDataSource> ?: emptyList())
        }
    }

    override fun <T : RawDataSource?> dataSourceChanged(
        manager: DataSourceManager<T>?,
        dataSource: T?,
    ) {
        if (dataSource is LocalDataSource) {
            ensureToolbarIsVisibleIfNecessary(manager?.dataSources as List<LocalDataSource> ?: emptyList())
        }
    }

    private fun ensureToolbarIsVisibleIfNecessary(dataSources: List<LocalDataSource>) {
        toolbar.refreshDataSources(dataSources)

        if (!editor.hasHeaderComponent()) {
            if (isEditingJavaFileWithMongoDBRelatedCode()) {
                editor.headerComponent = toolbar.panel
            }
        } else {
            if (!isEditingJavaFileWithMongoDBRelatedCode()) {
                editor.headerComponent = null
            }
        }
    }

    private fun ensureToolbarIsVisibleIfNecessary() {
        val dataSources =
            editor.project?.let {
                DataSourceManager.byDataSource(
                    it,
                    LocalDataSource::class.java,
                )
            }?.dataSources ?: emptyList()

        ensureToolbarIsVisibleIfNecessary(dataSources)
    }

    private fun isEditingJavaFileWithMongoDBRelatedCode(): Boolean {
        val project = editor.project ?: return false
        val psiFile = PsiManager.getInstance(project).findFile(editor.virtualFile) ?: return false
        if (psiFile.language != JavaLanguage.INSTANCE) {
            return false
        }

        val javaPsiFile = psiFile as PsiJavaFile
        return arrayOf(
            this::usesJavaDriver,
        ).any { it(javaPsiFile) }
    }

    private fun usesJavaDriver(psiFile: PsiJavaFile): Boolean {
        return psiFile.importList?.allImportStatements?.any {
            it.importReference?.canonicalText?.startsWith("com.mongodb") ?: false
        } ?: false
    }

    override fun beforeChildAddition(event: PsiTreeChangeEvent) {
    }

    override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
    }

    override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
    }

    override fun beforeChildMovement(event: PsiTreeChangeEvent) {
    }

    override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
    }

    override fun beforePropertyChange(event: PsiTreeChangeEvent) {
    }

    override fun childAdded(event: PsiTreeChangeEvent) {
        ensureToolbarIsVisibleIfNecessary()
    }

    override fun childRemoved(event: PsiTreeChangeEvent) {
        ensureToolbarIsVisibleIfNecessary()
    }

    override fun childReplaced(event: PsiTreeChangeEvent) {
        ensureToolbarIsVisibleIfNecessary()
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
        ensureToolbarIsVisibleIfNecessary()
    }

    override fun childMoved(event: PsiTreeChangeEvent) {
    }

    override fun propertyChanged(event: PsiTreeChangeEvent) {
    }
}
