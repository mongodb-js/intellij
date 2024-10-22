/**
 * Represents the main frame of IDEA. The one you see when you open a project, the whole window.
 * Reference code taken from:
 * https://github.com/JetBrains/intellij-ui-test-robot/blob/master/ui-test-example/src/test/kotlin/org/intellij/examples/simple/plugin/pages/IdeaFrame.kt
 */

package com.mongodb.jbplugin.fixtures.components.idea

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.steps.CommonSteps
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import com.mongodb.jbplugin.fixtures.MongoDbServerUrl
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.findVisible
import org.junit.jupiter.api.Assertions.assertTrue
import org.owasp.encoder.Encode
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

/**
 * Fixture that represents the frame itself. You can add more functions here if you want to interact
 * with the frame.
 *
 * @param remoteRobot
 * @param remoteComponent
 */
@FixtureName("Idea frame")
@DefaultXpath("IdeFrameImpl type", "//div[@class='IdeFrameImpl']")
class IdeaFrame(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent,
) : CommonContainerFixture(remoteRobot, remoteComponent) {
    fun openFile(path: String, closeOpenedFiles: Boolean = true) {
        step("Opening file at path $path with closeOpenedFiles=$closeOpenedFiles") {
            if (closeOpenedFiles) {
                this.closeAllFiles()
            }

            val escapedPath = Encode.forJavaScript(path)

            runJs(
                """
            importPackage(com.intellij.openapi.fileEditor)
            importPackage(com.intellij.openapi.vfs)
            importPackage(com.intellij.openapi.wm.impl)
            importClass(com.intellij.openapi.application.ApplicationManager)
            importClass(com.intellij.openapi.fileEditor.FileEditorManager)
            
            const path = '$escapedPath'
            const frameHelper = ProjectFrameHelper.getFrameHelper(component)
            if (frameHelper) {
                const project = frameHelper.getProject()
                const projectPath = project.getBasePath()
                const file = LocalFileSystem.getInstance().findFileByPath(projectPath + '/' + path)
                const openFileFunction = new Runnable({
                    run: function() {
                        const fileEditorManager = FileEditorManager.getInstance(project);
                        const fileDescriptor = new OpenFileDescriptor(
                            project,
                            file
                        );
                        fileEditorManager.openTextEditor(fileDescriptor, true)
                    }
                })
                ApplicationManager.getApplication().invokeAndWait(openFileFunction)
            }
        """,
                true,
            )
        }
        CommonSteps(remoteRobot).wait(1)
    }

    fun currentTab(): TextEditorFixture = remoteRobot.findVisible(
        TextEditorFixture.locator,
        Duration.ofSeconds(1)
    )

    fun addDataSourceWithUrl(
        name: String,
        url: MongoDbServerUrl,
    ) {
        step("Adding DataSource with name=$name, url=$url") {
            runJs(
                """
                const LocalDataSourceManager = global.get('loadDataGripPluginClass')(
                    'com.intellij.database.dataSource.LocalDataSourceManager'
                )
    
                const DatabaseDriverManager = global.get('loadDataGripPluginClass')(
                    'com.intellij.database.dataSource.DatabaseDriverManager'
                )
    
                const LocalDataSource = global.get('loadDataGripPluginClass')(
                    'com.intellij.database.dataSource.LocalDataSource'
                )
    
                const DatabaseDriverValidator = global.get('loadDataGripPluginClass')(
                    'com.intellij.database.dataSource.validation.DatabaseDriverValidator'
                )
    
                const DatabaseConfigEditor = global.get('loadDataGripPluginClass')(
                    'com.intellij.database.view.ui.DatabaseConfigEditor'
                )
    
                importClass(com.intellij.openapi.project.Project)
                importPackage(com.intellij.openapi.progress)
                importPackage(com.intellij.openapi.wm.impl)
                importPackage(com.intellij.database.view.ui)
    
                const frameHelper = ProjectFrameHelper.getFrameHelper(component)
                if (frameHelper) {
                    const project = frameHelper.getProject()
                    
                    const dataSourceManager = LocalDataSourceManager.getMethod("getInstance", Project).invoke(null, project)
                    const driverManager = DatabaseDriverManager.getMethod("getInstance").invoke(null)
                    const jdbcDriver = driverManager.getDriver("mongo")
                    
                    const dataSource = LocalDataSource.newInstance()
                    dataSource.setName("$name")
                    dataSource.setUrl("${url.value}")
                    dataSource.setConfiguredByUrl(true)
                    dataSource.setDatabaseDriver(jdbcDriver)
                    dataSourceManager.addDataSource(dataSource)
                    
                    global.put("dataSource", dataSource);
                    DatabaseDriverValidator.getMethod("createDownloaderTask", LocalDataSource, DatabaseConfigEditor)
                        .invoke(null, dataSource, null)
                        .run(new EmptyProgressIndicator())
                }
                """.trimIndent(),
                runInEdt = true,
            )
        }
        CommonSteps(remoteRobot).wait(1)
    }

    fun waitUntilConnectedToMongoDb(name: String, timeout: Duration = Duration.ofMinutes(1)) {
        eventually(timeout) {
            step("Waiting for DataSource $name to connect, timeout=$timeout") {
                assertTrue(
                    callJs<Boolean>(
                        """
                    importClass(java.lang.System)

                    const DatabaseConnectionManager = global.get('loadDataGripPluginClass')(
                        'com.intellij.database.dataSource.DatabaseConnectionManager'
                    )
                    
                    const connectionManager = DatabaseConnectionManager.getMethod("getInstance").invoke(null)
                    const activeConnections = connectionManager.getActiveConnections()
                    var connected = false;
                    
                    for (connection of activeConnections) {                    
                        if(connection.getConnectionPoint().getDataSource().name.equals("$name")) {
                            try {
                                connected = !connection.getRemoteConnection().isClosed() && 
                                             connection.getRemoteConnection().isValid(10)
                            } catch (e) {
                                System.err.println(e.toString())
                            }
                            
                            if (connected) {
                                break
                            }
                        }
                    }
                    
                    connected
                        """.trimIndent(),
                        runInEdt = true
                    )
                )
            }
        }
        CommonSteps(remoteRobot).wait(1)
    }

    fun disablePowerSaveMode() {
        step("Disable Power Save Mode") {
            runJs(
                """
            importClass(com.intellij.ide.PowerSaveMode)
            importClass(com.intellij.openapi.application.ApplicationManager)
            
            const disableIt = new Runnable({
                    run: function() {
                        PowerSaveMode.setEnabled(false)
                    }
                })
        
            ApplicationManager.getApplication().invokeLater(disableIt)
                """.trimIndent()
            )
        }
        CommonSteps(remoteRobot).wait(1)
    }

    private fun waitUntilModulesAreLoaded(timeout: Duration = Duration.ofMinutes(1)) {
        step("Wait until modules are loaded") {
            waitFor(
                duration = timeout,
                description = "Modules to finish loading",
                errorMessage = "Modules did not load"
            ) {
                callJs(
                    """
                    importPackage(com.intellij.openapi.wm.impl)
                    importClass(com.intellij.openapi.module.ModuleManager)
                    const frameHelper = ProjectFrameHelper.getFrameHelper(component)
                    const project = frameHelper.getProject()
                    const modules = ModuleManager.getInstance(project).getModules()
            
                    modules.length > 0
                    """.trimIndent(),
                    runInEdt = true
                )
            }
        }
    }

    private fun waitForSmartMode(timeout: Int = 5) {
        // exiting smart mode does not mean we are in smart mode!
        // so try a few times and wish for luck, there is no better API it seems.
        // Reasoning: you are in dumb mode, you load some project metadata, go to smart mode
        // then realise that you have dependencies to download and index, so you go back to dumb
        // mode until everything is done.
        // happily enough, this won't take time if smart mode is already on, so it should
        // be fast.
        step("Optimistically waiting for smart mode") {
            for (i in 0..10) {
                CommonSteps(remoteRobot).waitForSmartMode(timeout)
            }
        }
    }

    fun waitUntilProjectIsInSync(
        modulesTimeout: Duration = Duration.ofMinutes(10),
        smartModeTimeout: Int = 5
    ) {
        step("For project to be in sync") {
            waitUntilModulesAreLoaded(modulesTimeout)
            waitForSmartMode(smartModeTimeout)
        }
    }

    fun hideIntellijAiAd() {
        step("Hide IntelliJ AI Ad (uses a lot of space in a small window)") {
            tryHidingAiAdOnOldUI()
            tryHidingAiAdOnNewUI()
        }
    }

    private fun tryHidingAiAdOnNewUI() {
        step("Attempting to hide AI ad on new UI") {
            runCatching {
                val aiMenu = remoteRobot.find<JButtonFixture>(
                    byXpath("//div[@accessiblename='AI Assistant']")
                )
                aiMenu.rightClick()
                remoteRobot.find<JButtonFixture>(
                    byXpath("//div[@text='Hide']")
                ).click()
            }
        }
    }

    private fun tryHidingAiAdOnOldUI() {
        step("Attempting to hide AI ad on old UI") {
            runCatching {
                val aiMenu = remoteRobot.find<JButtonFixture>(
                    byXpath("//div[@tooltiptext='Install AI Assistant']")
                )
                aiMenu.rightClick()
                remoteRobot.find<JButtonFixture>(
                    byXpath("//div[@text='Remove from Sidebar']")
                ).click()
            }
        }
    }

    fun cleanDataSources() {
        step("Cleaning DataSources") {
            // From time to time this fails with write safe context errors
            // but given that the removal happens anyways, we are silently ignoring
            // the error here
            runCatching {
                runJs(
                    """
                const LocalDataSourceManager = global.get('loadDataGripPluginClass')(
                    'com.intellij.database.dataSource.LocalDataSourceManager'
                )
                
                importClass(java.lang.System)
                importClass(com.intellij.openapi.project.Project)
                importClass(com.intellij.openapi.util.Key)
                importPackage(com.intellij.openapi.progress)
                importPackage(com.intellij.openapi.wm.impl)
                importPackage(com.intellij.database.view.ui)
                importClass(com.intellij.openapi.application.ApplicationManager)
    
                const frameHelper = ProjectFrameHelper.getFrameHelper(component)
                const project = frameHelper.getProject()
                const dataSourceManager = LocalDataSourceManager.getMethod("getInstance", Project).invoke(null, project)
                const dataSources = dataSourceManager.getDataSources();
                for (let i = 0; i < dataSources.size(); i++) {
                    dataSourceManager.removeDataSource(dataSources.get(i));
                }
                    """.trimIndent(),
                    runInEdt = true,
                )
            }
        }
        CommonSteps(remoteRobot).wait(1)
    }

    fun closeAllFiles() {
        step("Closing all files") {
            runJs(
                """
            importPackage(com.intellij.openapi.fileEditor)
            importPackage(com.intellij.openapi.vfs)
            importPackage(com.intellij.openapi.wm.impl)
            importClass(com.intellij.openapi.application.ApplicationManager)
            
            const frameHelper = ProjectFrameHelper.getFrameHelper(component)
            if (frameHelper) {
                const project = frameHelper.getProject()
                const closeEditorsFunction = new Runnable({
                    run: function() {
                         const editorManager = FileEditorManager.getInstance(project)
                         const files = editorManager.openFiles
                         files.forEach((file) => { editorManager.closeFile(file) })
                    }
                })

                ApplicationManager.getApplication().invokeLater(closeEditorsFunction)
            }
        """,
                true,
            )
        }
        CommonSteps(remoteRobot).wait(1)
    }
}

/**
 * Returns a reference to the idea frame. In the reference code this accepts a function
 * that is applied to the frame.
 *
 * @return
 */
fun RemoteRobot.ideaFrame(): IdeaFrame = findVisible()

/**
 * Returns the idea frame if visible, it doesn't wait to be visible, instead returns null.
 *
 * @see ideaFrame in case you need to wait for it to be visible.
 *
 * @return
 */
fun RemoteRobot.maybeIdeaFrame(): IdeaFrame? =
    runCatching {
        find<IdeaFrame>(timeout = 15.milliseconds.toJavaDuration())
    }.getOrNull()
