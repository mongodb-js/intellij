/**
 * Action that loads all Atlas clusters for the current user.
 */

package com.mongodb.jbplugin.actions

import com.intellij.database.dataSource.DatabaseDriverManagerImpl
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.SchemaControl
import com.intellij.database.psi.DataSourceManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchChildOnUi
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.mongodb.accessadapter.ClusterProvider
import com.mongodb.accessadapter.Group
import com.mongodb.accessadapter.atlas.AtlasClusterProvider
import com.mongodb.accessadapter.atlas.AtlasDatabaseUserProvider
import com.mongodb.jbplugin.settings.useSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent


/**
 * Service that implements the action.
 *
 * @param project
 * @param coroutineScope
 */
@Service(Service.Level.PROJECT)
internal class CreateAtlasFreeClusterActionService(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) {
    fun createCluster() = coroutineScope.launch {
        val settings = useSettings()
        val (publicKey, privateKey) = settings.atlasApiKey.split(":")
        val clusterProvider: ClusterProvider = AtlasClusterProvider(publicKey, privateKey)

        coroutineScope.launchChildOnUi {
            val createClusterDialog = CreateAtlasFreeClusterDialogWrapper(project.name)
            coroutineScope.launch {
                val groups = clusterProvider.listOfGroups()
                createClusterDialog.setGroups(groups)
            }

            if (!createClusterDialog.showAndGet()) {
                return@launchChildOnUi
            }

            val cluster = clusterProvider.createFreeCluster(
                createClusterDialog.clusterName,
                createClusterDialog.atlasGroup.id,
                false,
                sampleDataset = createClusterDialog.shouldAddSampleData
            )

            if (createClusterDialog.shouldCreateFirstUser) {
                AtlasDatabaseUserProvider(publicKey, privateKey).createUser(
                    createClusterDialog.atlasGroup.id,
                    createClusterDialog.userName,
                    String(createClusterDialog.password)
                )
            }

            val dataSourceManager = DataSourceManager.byDataSource(project, LocalDataSource::class.java)
            val instance = DatabaseDriverManagerImpl.getInstance()
            val driver = instance.getDriver("mongo")
            val dataSource = LocalDataSource().apply {
                name = cluster.clusterUrl
                setUrlSmart(cluster.clusterUrl)
                setSchemaControl(SchemaControl.AUTOMATIC)
                groupName = "Atlas / ${cluster.organizationName} / ${cluster.groupName}"
                databaseDriver = driver
                if (createClusterDialog.shouldCreateFirstUser) {
                    username = createClusterDialog.userName
                }
            }

            dataSourceManager?.addDataSource(dataSource)
        }
    }
}

/**
 * Action that loads current account Atlas Clusters.
 */
class CreateAtlasFreeClusterAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.project!!.getService(CreateAtlasFreeClusterActionService::class.java).createCluster()
    }
}

class CreateAtlasFreeClusterDialogWrapper(
    defaultClusterName: String
) : DialogWrapper(true) {
    private val clusterNameComponent = JBTextField(defaultClusterName)
    private val projectSelectComponent = ComboBox<String>(emptyArray())
    private val userNameComponent = JBTextField()
    private val passwordComponent = JBPasswordField()
    private val shouldAddSampleDataComponent = JBCheckBox("Add sample data to the cluster", false)
    private val shouldCreateFirstUserComponent = JBCheckBox("Create first user for this cluster", true)
    private var groupReferenceList: List<Group> = emptyList()

    val clusterName get() = clusterNameComponent.text
    val atlasGroup get() = groupReferenceList.find { it.name == projectSelectComponent.selectedItem }!!
    val userName get() = userNameComponent.text
    val password get() = passwordComponent.password
    val shouldAddSampleData get() = shouldAddSampleDataComponent.isSelected
    val shouldCreateFirstUser get() = shouldCreateFirstUserComponent.isSelected

    fun setGroups(groups: List<Group>) {
        groupReferenceList = groups
        projectSelectComponent.model = DefaultComboBoxModel(groups.map { it.name }.toTypedArray())
        isOKActionEnabled = true
    }

    init {
        title = "Create free MongoDB Atlas cluster"
        isOKActionEnabled = false
        isModal = true
        setOKButtonText("Create")
        setCancelButtonText("Cancel")
        init()
    }

    override fun createCenterPanel(): JComponent? {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Cluster name", clusterNameComponent)
            .addLabeledComponent("Project", projectSelectComponent)
            .addSeparator()
            .addComponentToRightColumn(shouldCreateFirstUserComponent)
            .addLabeledComponent("User", userNameComponent)
            .addLabeledComponent("Password", passwordComponent)
            .addSeparator()
            .addComponentToRightColumn(shouldAddSampleDataComponent)
            .panel
    }

}