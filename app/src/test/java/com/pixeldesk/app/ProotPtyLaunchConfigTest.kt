package com.pixeldesk.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotPtyLaunchConfigTest {
    @Test
    fun buildsInteractiveProotArgumentsWithPrivateWorkspaceBindings() {
        val fixture = fixture()
        try {
            val args = buildProotPtyArguments(fixture.workspace, "/usr/bin/dash")
            assertTrue(args.contains("-r"))
            assertTrue(args.contains(fixture.workspace.rootFilesystem.canonicalPath))
            assertTrue(args.contains("${fixture.workspace.home.canonicalPath}:/root"))
            assertTrue(args.contains("${fixture.workspace.downloads.canonicalPath}:/root/Downloads"))
            assertTrue(args.contains("${fixture.workspace.temporary.canonicalPath}:/tmp"))
            assertEquals("/usr/bin/dash", args[args.size - 2])
            assertEquals("-i", args.last())
            assertTrue(!args.contains("-c"))
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun configuresPtyTerminalAndExplicitProotLoader() {
        val fixture = fixture()
        try {
            val environment = buildProotPtyEnvironment(fixture.files, fixture.workspace)
            assertTrue(environment.contains("PROOT_LOADER=${fixture.files.loader.absolutePath}"))
            assertTrue(environment.contains("PROOT_TMP_DIR=${fixture.workspace.temporary.canonicalPath}"))
            assertTrue(environment.contains("TERM=xterm-256color"))
            assertTrue(environment.contains("COLORTERM=truecolor"))
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    private data class Fixture(
        val root: File,
        val workspace: EnvironmentWorkspace,
        val files: ProotRuntimeFiles
    )

    private fun fixture(): Fixture {
        val root = Files.createTempDirectory("pixeldesk-pty-config").toFile()
        val workspaceDir = File(root, "test").apply { mkdirs() }
        val rootfs = File(workspaceDir, "rootfs").apply { mkdirs() }
        val home = File(workspaceDir, "home").apply { mkdirs() }
        val downloads = File(workspaceDir, "downloads").apply { mkdirs() }
        val temporary = File(workspaceDir, "tmp").apply { mkdirs() }
        val runtime = File(root, "runtime").apply { mkdirs() }
        val executable = File(runtime, "proot").apply { writeText("fake"); setExecutable(true) }
        val loader = File(runtime, "loader").apply { writeText("fake") }
        return Fixture(
            root,
            EnvironmentWorkspace("test", "Test", workspaceDir, rootfs, home, downloads, temporary),
            ProotRuntimeFiles(executable, loader, null)
        )
    }
}
