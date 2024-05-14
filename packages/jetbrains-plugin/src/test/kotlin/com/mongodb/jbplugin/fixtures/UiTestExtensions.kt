/**
 * Extensions for UI tests. All these classes and functions are helpers for UI tests
 * using the RemoteRobot.
 */

package com.mongodb.jbplugin.fixtures

import com.automation.remarks.junit5.Video
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.steps.CommonSteps
import com.intellij.remoterobot.utils.DefaultHttpClient.client
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.Path

/**
 * Extension annotation for tests. Use it for UI tests.
 */
@ExtendWith(UiTestExtension::class)
@Tag("UI")
annotation class UiTest

/**
 * Extension annotation for tests.
 * Use it for tests that require to load a project by name.
 * Projects are saved in the resources folder, in project-fixtures/NAME_OF_PROJECT
 *
 * @property value
 */
@Target(AnnotationTarget.FUNCTION)
@Video
annotation class RequiresProject(val value: String)

/**
 * Test Extension for JUnit. Shouldn't be used directly.
 *
 * @see UiTest
 */
private class UiTestExtension : BeforeAllCallback,
    BeforeTestExecutionCallback,
    AfterTestExecutionCallback,
    ParameterResolver {
    private val remoteRobotUrl: String = "http://localhost:8082"
    private lateinit var remoteRobot: RemoteRobot

    override fun supportsParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Boolean =
        parameterContext?.parameter?.type?.equals(RemoteRobot::class.java) ?: false

    override fun resolveParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Any =
        remoteRobot

    override fun beforeAll(context: ExtensionContext?) {
        remoteRobot = RemoteRobot(remoteRobotUrl)
    }

    override fun beforeTestExecution(context: ExtensionContext?) {
        val requiresProject = context?.requiredTestMethod?.annotations
            ?.find { annotation -> annotation.annotationClass == RequiresProject::class } as RequiresProject?

        CommonSteps(remoteRobot).closeProject()
        requiresProject?.let {
            // If we have the @RequireProject annotation, load that project on startup
            CommonSteps(remoteRobot).openProject(
                Path("src/test/resources/project-fixtures/${requiresProject.value}").toAbsolutePath().toString()
            )
        }
    }

    override fun afterTestExecution(context: ExtensionContext?) {
        val testMethod = context?.requiredTestMethod ?: throw IllegalStateException("test method is null")
        val testMethodName = testMethod.name
        val testFailed: Boolean = context.executionException?.isPresent ?: false
        if (testFailed) {
            saveScreenshot(testMethodName)
            saveIdeaFrames(testMethodName)
            saveHierarchy(testMethodName)
        }

        CommonSteps(remoteRobot).closeProject()
    }

    private fun saveScreenshot(testName: String) {
        fetchScreenShot().save(testName)
    }

    private fun saveHierarchy(testName: String) {
        "build/reports".saveFile(remoteRobotUrl, "hierarchy-$testName.html")
        if (File("build/reports/styles.css").exists().not()) {
            "build/reports".saveFile("$remoteRobotUrl/styles.css", "styles.css")
        }
    }

    private fun String.saveFile(
        url: String,
        name: String
    ): File {
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        return File(this).apply {
            mkdirs()
        }.resolve(name).apply {
            writeText(response.body?.string() ?: "")
        }
    }

    private fun BufferedImage.save(name: String) {
        val bytes = ByteArrayOutputStream().use { bos ->
            ImageIO.write(this, "png", bos)
            bos.toByteArray()
        }

        File("build/reports").apply { mkdirs() }.resolve("$name.png").writeBytes(bytes)
    }

    private fun saveIdeaFrames(testName: String) {
        remoteRobot.findAll<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))
            .forEachIndexed { index, frame ->
                val pic = try {
                    frame.callJs<ByteArray>(
                        """
                        importPackage(java.io)
                        importPackage(javax.imageio)
                        importPackage(java.awt.image)
                        const screenShot = new BufferedImage(
                            component.getWidth(), 
                            component.getHeight(), 
                            BufferedImage.TYPE_INT_ARGB
                        );
                        component.paint(screenShot.getGraphics())
                        let pictureBytes;
                        const baos = new ByteArrayOutputStream();
                        try {
                            ImageIO.write(screenShot, "png", baos);
                            pictureBytes = baos.toByteArray();
                        } finally {
                          baos.close();
                        }
                        pictureBytes;   
            """, true
                    )
                } catch (e: Throwable) {
                    e.printStackTrace()
                    throw e
                }
                pic.inputStream().use {
                    ImageIO.read(it)
                }.save(testName + "_" + index)
            }
    }

    private fun fetchScreenShot(): BufferedImage = remoteRobot.callJs<ByteArray>(
        """
            importPackage(java.io)
            importPackage(javax.imageio)
            const screenShot = new java.awt.Robot().createScreenCapture(
                new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())
            );
            let pictureBytes;
            const baos = new ByteArrayOutputStream();
            try {
                ImageIO.write(screenShot, "png", baos);
                pictureBytes = baos.toByteArray();
            } finally {
              baos.close();
            }
            pictureBytes;
        """
    )
        .inputStream()
        .use {
            ImageIO.read(it)
        }
}

/**
 * Removes all html tags and only keeps the plain text.
 *
 * @return
 */
fun String.stripHtml(): String = Jsoup.clean(this, Safelist.none())