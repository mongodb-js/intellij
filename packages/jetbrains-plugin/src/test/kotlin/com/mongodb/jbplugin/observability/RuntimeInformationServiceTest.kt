package com.mongodb.jbplugin.observability

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.util.SystemInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic

class RuntimeInformationServiceTest {
    private lateinit var permanentInstallationId: MockedStatic<PermanentInstallationID>
    private lateinit var applicationInfo: MockedStatic<ApplicationInfo>
    private lateinit var systemInfo: MockedStatic<SystemInfo>

    @BeforeEach
    fun setUp() {
        permanentInstallationId = mockStatic(PermanentInstallationID::class.java)
        applicationInfo = mockStatic(ApplicationInfo::class.java)
        systemInfo = mockStatic(SystemInfo::class.java)
    }

    @AfterEach
    fun tearDown() {
        permanentInstallationId.closeOnDemand()
        applicationInfo.closeOnDemand()
        systemInfo.closeOnDemand()
    }

    @Test
    fun `loads all information from runtime`() {
        permanentInstallationId.`when`<String> {
            PermanentInstallationID.get()
        }.thenReturn("123456")

        val appInfoInstance = mock<ApplicationInfo>().apply {
            `when`(this.fullApplicationName).thenReturn("Test Application")
            `when`(this.fullVersion).thenReturn("1984.1.1")
        }

        applicationInfo.`when`<ApplicationInfo> {
            ApplicationInfo.getInstance()
        }.thenReturn(appInfoInstance)

        systemInfo.`when`<String> {
            SystemInfo.getOsNameAndVersion()
        }.thenReturn("Test OS 2708")

        val service = RuntimeInformationService()

        val runtimeInfo = service.get()
        assertEquals("123456", runtimeInfo.userId)
        assertEquals("Test OS 2708", runtimeInfo.osName)
        assertNotNull(runtimeInfo.arch)
        assertNotNull(runtimeInfo.jvmVendor)
        assertNotNull(runtimeInfo.jvmVersion)
        assertEquals("Test Application", runtimeInfo.applicationName)
        assertEquals("1984.1.1", runtimeInfo.buildVersion)
    }

    @Test
    fun `loads the default value on exception`() {
        permanentInstallationId.`when`<String> {
            PermanentInstallationID.get()
        }.thenThrow(RuntimeException("Oops, I did it again."))

        val service = RuntimeInformationService()

        val runtimeInfo = service.get()
        assertEquals("<userId>", runtimeInfo.userId)
    }
}
