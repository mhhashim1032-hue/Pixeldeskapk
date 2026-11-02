package com.pixeldesk.app

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotInteractiveSessionTest {
    @Test
    fun preservesShellStateBetweenCommands() = runBlocking {
        val fixture = fixture()
        val session = ProotInteractiveSession.start(fixture.files, fixture.workspace)
        try {
            val first = session.execute("PIXEL_DESK_TEST=value")
            val second = session.execute("printf '%s\\n' \"\$PIXEL_DESK_TEST\"")
            assertTrue(first.sessionAlive)
            assertTrue(second.success)
            assertTrue(second.output.contains("value"))
        } finally {
            session.close()
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun returnsExitStatusAndCapturesMergedOutput() = runBlocking {
        val fixture = fixture()
        val session = ProotInteractiveSession.start(fixture.files, fixture.workspace)
        try {
            val result = session.execute("echo terminal-output; echo terminal-error >&2; false")
            assertEquals(1, result.exitCode)
            assertFalse(result.success)
            assertTrue(result.output.contains("terminal-output"))
            assertTrue(result.output.contains("terminal-error"))
            assertTrue(result.sessionAlive)
        } finally {
            session.close()
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun shellExitIsNotReportedAsTimeout() = runBlocking {
        val fixture = fixture()
        val session = ProotInteractiveSession.start(fixture.files, fixture.workspace)
        try {
            val result = session.execute("exit")
            assertFalse(result.sessionAlive)
            assertFalse(result.timedOut)
        } finally {
            session.close()
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun timeoutTerminatesSession() = runBlocking {
        val fixture = fixture()
        val session = ProotInteractiveSession.start(fixture.files, fixture.workspace)
        try {
            val result = session.execute("exec sleep 10", 1_000)
            assertTrue(result.timedOut)
            assertFalse(result.sessionAlive)
        } finally {
            session.close()
            fixture.root.deleteRecursively()
        }
    }

    private data class Fixture(
        val root: File,
        val files: ProotRuntimeFiles,
        val workspace: EnvironmentWorkspace
    )

    private fun fixture(): Fixture {
        val root = Files.createTempDirectory("pixeldesk-interactive").toFile()
        val workspaceDir = File(root, "workspace").apply { mkdirs() }
        val rootfs = File(workspaceDir, "rootfs").apply { mkdirs() }
        val home = File(workspaceDir, "home").apply { mkdirs() }
        val downloads = File(workspaceDir, "downloads").apply { mkdirs() }
        val temporary = File(workspaceDir, "tmp").apply { mkdirs() }
        File(rootfs, "etc").mkdirs()
        File(rootfs, "usr/bin").mkdirs()
        File(rootfs, "etc/os-release").writeText("ID=debian\nVERSION_ID=\"12\"\n")
        File(rootfs, "usr/bin/dpkg").writeText("dpkg placeholder")
        val elf = ByteArray(20)
        elf[0] = 0x7f
        elf[1] = 'E'.code.toByte()
        elf[2] = 'L'.code.toByte()
        elf[3] = 'F'.code.toByte()
        elf[4] = 2
        elf[5] = 1
        elf[18] = 183.toByte()
        File(rootfs, "usr/bin/dash").writeBytes(elf)

        val runtime = File(root, "runtime").apply { mkdirs() }
        val proot = File(runtime, "fake-proot").apply {
            writeText("#!/bin/sh\nexec /bin/sh -i\n")
            setExecutable(true)
        }
        val loader = File(runtime, "loader").apply { writeText("loader") }
        return Fixture(
            root,
            ProotRuntimeFiles(proot, loader, null),
            EnvironmentWorkspace("test", "Test", workspaceDir, rootfs, home, downloads, temporary)
        )
    }
}
