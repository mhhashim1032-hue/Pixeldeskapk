package com.pixeldesk.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxAppPreflightTest {
    @Test
    fun parsesFirstExecTokenWithDesktopEntryDoubleQuotes() {
        assertEquals("firefox", LinuxAppPreflight.executableToken("\"firefox\" --new-window %U"))
        assertEquals("/usr/bin/editor", LinuxAppPreflight.executableToken("/usr/bin/editor --open %f"))
    }

    @Test
    fun rejectsShellSyntaxTraversalAndMalformedExecValues() {
        assertNull(LinuxAppPreflight.executableToken("browser; rm -rf /"))
        assertNull(LinuxAppPreflight.executableToken("../../tmp/unsafe"))
        assertNull(LinuxAppPreflight.executableToken("\"unterminated"))
        assertNull(LinuxAppPreflight.executableToken("%k"))
    }

    @Test
    fun createsOnlyQuotedCommandAvailabilityChecks() {
        val apps = listOf(
            LinuxDesktopApp("Browser", "firefox %U", null, "/usr/share/applications/firefox.desktop", false),
            LinuxDesktopApp("Editor", "\"text editor\" %f", null, "/usr/share/applications/editor.desktop", false)
        )
        val command = LinuxAppPreflight.buildProbeCommand(apps)
        assertTrue(command.contains("command -v 'firefox'"))
        assertTrue(command.contains("command -v 'text editor'").not())
        assertTrue(command.contains("printf '%s\\t'"))
    }

    @Test
    fun parsesOnlyValidAvailabilityRows() {
        val result = LinuxAppPreflight.parseProbeResults(
            "firefox\tAVAILABLE\n" +
                "missing-app\tMISSING\n" +
                "bad name\tAVAILABLE\n" +
                "unknown\tOTHER\n"
        )
        assertEquals(mapOf("firefox" to true, "missing-app" to false), result)
    }
}
