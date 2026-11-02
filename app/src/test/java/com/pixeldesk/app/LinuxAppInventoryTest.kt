package com.pixeldesk.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxAppInventoryTest {
    @Test
    fun parsesVisibleApplicationEntriesAndSortsByName() {
        val output = """
            __PIXEL_DESK_ENTRY__
            PATH=/usr/share/applications/zeta.desktop
            Type=Application
            Name=Zeta Editor
            Exec=zeta %F
            Icon=zeta
            Terminal=false
            __PIXEL_DESK_ENTRY__
            PATH=/usr/share/applications/alpha.desktop
            Type=Application
            Name=Alpha Browser
            Exec=alpha --new-window
        """.trimIndent()
        val apps = LinuxAppInventory.parseDesktopEntries(output)
        assertEquals(listOf("Alpha Browser", "Zeta Editor"), apps.map { it.name })
        assertEquals("/usr/share/applications/zeta.desktop", apps[1].desktopFile)
        assertEquals("zeta", apps[1].icon)
        assertFalse(apps[1].terminal)
    }

    @Test
    fun filtersHiddenNoDisplayAndNonApplicationEntries() {
        val output = """
            __PIXEL_DESK_ENTRY__
            PATH=/usr/share/applications/hidden.desktop
            Name=Hidden
            Exec=hidden
            Hidden=true
            __PIXEL_DESK_ENTRY__
            PATH=/usr/share/applications/internal.desktop
            Name=Internal
            Exec=internal
            NoDisplay=true
            __PIXEL_DESK_ENTRY__
            PATH=/usr/share/applications/link.desktop
            Type=Link
            Name=Web Link
            Exec=browser
        """.trimIndent()
        assertTrue(LinuxAppInventory.parseDesktopEntries(output).isEmpty())
    }

    @Test
    fun rejectsEntriesWithoutAbsoluteDesktopFileOrExec() {
        val output = """
            __PIXEL_DESK_ENTRY__
            PATH=relative.desktop
            Name=Bad path
            Exec=bad
            __PIXEL_DESK_ENTRY__
            PATH=/usr/share/applications/no-exec.desktop
            Name=No command
        """.trimIndent()
        assertTrue(LinuxAppInventory.parseDesktopEntries(output).isEmpty())
    }

    @Test
    fun preservesTerminalFlagAndExecArgumentsAsMetadataOnly() {
        val output = """
            __PIXEL_DESK_ENTRY__
            PATH=/usr/share/applications/terminal.desktop
            Type=Application
            Name=Terminal Tool
            Exec=terminal --working-directory=%f
            Terminal=true
        """.trimIndent()
        val app = LinuxAppInventory.parseDesktopEntries(output).single()
        assertTrue(app.terminal)
        assertEquals("terminal --working-directory=%f", app.executable)
    }
}
