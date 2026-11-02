package com.pixeldesk.app

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotCommandRunnerTest {
    @Test
    fun executesCommandAndCapturesBothOutputStreams() = runBlocking {
        val fixture = fixture("printf 'linux stdout\n'; printf 'linux stderr\n' >&2; exit 0")
        try {
            val result = ProotCommandRunner.run(
                fixture.files,
                fixture.workspace,
                "uname -m",
                2_000
            )
            assertTrue(result.success)
            assertEquals(0, result.exitCode)
            assertTrue(result.stdout.contains("linux stdout"))
            assertTrue(result.stderr.contains("linux stderr"))
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun returnsNonZeroExitCodeWithoutMarkingSuccess() = runBlocking {
        val fixture = fixture("echo failed >&2; exit 7")
        try {
            val result = ProotCommandRunner.run(fixture.files, fixture.workspace, "false", 2_000)
            assertFalse(result.success)
            assertEquals(7, result.exitCode)
            assertTrue(result.stderr.contains("failed"))
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun terminatesCommandThatExceedsTimeout() = runBlocking {
        val fixture = fixture("exec sleep 5")
        try {
            val result = ProotCommandRunner.run(fixture.files, fixture.workspace, "sleep 5", 1_000)
            assertFalse(result.success)
            assertTrue(result.timedOut)
            assertTrue(result.stderr.contains("timed out"))
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun buildsRootfsAndPrivateWorkspaceBindingsAsSeparateArguments() {
        val fixture = fixture("echo ok")
        try {
            val args = ProotCommandRunner.buildArguments(
                fixture.workspace,
                "/usr/bin/dash",
                "echo 'hello world'"
            )
            assertTrue(args.contains("-0"))
            assertTrue(args.contains("-L"))
            assertTrue(args.contains(fixture.workspace.rootFilesystem.canonicalPath))
            assertTrue(args.contains("${fixture.workspace.home.canonicalPath}:/root"))
            assertTrue(args.contains("${fixture.workspace.downloads.canonicalPath}:/root/Downloads"))
            assertEquals("echo 'hello world'", args.last())
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun rejectsBlankCommands() = runBlocking {
        val fixture = fixture("echo ok")
        try {
            runCatching {
                ProotCommandRunner.run(fixture.files, fixture.workspace, "   ", 2_000)
            }.onSuccess { error("Blank commands must be rejected.") }
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    private data class Fixture(
        val root: File,
        val files: ProotRuntimeFiles,
        val workspace: EnvironmentWorkspace
    )

    private fun fixture(prootBody: String): Fixture {
        val root = Files.createTempDirectory("pixeldesk-proot-command").toFile()
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

        val runtimeDir = File(root, "runtime").apply { mkdirs() }
        val executable = File(runtimeDir, "fake-proot").apply {
            writeText("#!/bin/sh\n$prootBody\n")
            setExecutable(true)
        }
        val loader = File(runtimeDir, "loader").apply { writeText("loader") }
        return Fixture(
            root,
            ProotRuntimeFiles(executable, loader, null),
            EnvironmentWorkspace("test", "Test", workspaceDir, rootfs, home, downloads, temporary)
        )
    }
}
