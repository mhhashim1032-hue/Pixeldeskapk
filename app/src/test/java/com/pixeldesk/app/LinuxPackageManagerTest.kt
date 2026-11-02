package com.pixeldesk.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxPackageManagerTest {
    @Test
    fun parsesUniquePackageNames() {
        assertEquals(listOf("curl", "nano", "python3"), LinuxPackageManager.parsePackageNames("curl nano curl python3"))
    }

    @Test
    fun rejectsShellMetacharactersAndOptions() {
        listOf("nano;id", "$(id)", "curl && whoami", "-y", "foo/bar").forEach { input ->
            assertTrue(runCatching { LinuxPackageManager.parsePackageNames(input) }.isFailure)
        }
    }

    @Test
    fun limitsPackageBatchSize() {
        val input = (1..21).joinToString(" ") { "package$it" }
        assertTrue(runCatching { LinuxPackageManager.parsePackageNames(input) }.isFailure)
    }

    @Test
    fun buildsInstallAndRemoveCommandsOnlyFromValidatedNames() {
        val install = LinuxPackageManager.installOperation("curl python3")
        assertTrue(install.command.contains("apt-get -y --no-install-recommends install -- curl python3"))
        val remove = LinuxPackageManager.removeOperation("nano")
        assertTrue(remove.command.endsWith("apt-get -y remove -- nano"))
    }

    @Test
    fun quickInstallOnlyAcceptsCuratedPackageNames() {
        assertTrue(LinuxPackageManager.quickInstallOperation("curl").command.contains("install -- curl"))
        assertTrue(runCatching { LinuxPackageManager.quickInstallOperation("unknown-tool") }.isFailure)
    }

    @Test
    fun diagnosticsUseFixedCommands() {
        assertTrue(LinuxPackageManager.installedPackagesOperation().command.contains("dpkg-query"))
        assertTrue(LinuxPackageManager.systemInfoOperation().command.contains("dpkg --print-architecture"))
    }

    @Test
    fun rejectsEmptyPackageInput() {
        assertTrue(runCatching { LinuxPackageManager.installOperation("   ") }.isFailure)
    }
}
