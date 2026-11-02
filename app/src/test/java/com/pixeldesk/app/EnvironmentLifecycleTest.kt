package com.pixeldesk.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentLifecycleTest {
    @Test fun stateReportsStorageOnlyAndRejectsInvalidRootfs() {
        val root = createTempDir(prefix = "pixeldesk-lifecycle-")
        try {
            val workspace = workspace(root)
            assertEquals(EnvironmentState.STORAGE_ONLY, state(workspace))
            workspace.rootFilesystem.mkdirs()
            File(workspace.rootFilesystem, "etc").mkdirs()
            File(workspace.rootFilesystem, "etc/os-release").writeText("ID=ubuntu\nVERSION_ID=22.04\n")
            assertEquals(EnvironmentState.INVALID, state(workspace))
        } finally { root.deleteRecursively() }
    }

    @Test fun deletionRemovesOnlySelectedWorkspaceAndLeavesSibling() {
        val root = createTempDir(prefix = "pixeldesk-delete-")
        try {
            val environments = File(root, "environments").apply { mkdir() }
            val selected = File(environments, "debian-arm64").apply { mkdir() }
            File(selected, "home").mkdir()
            File(selected, "home/keep.txt").writeText("data")
            val sibling = File(environments, "test-env").apply { mkdir() }
            File(sibling, "marker.txt").writeText("untouched")
            assertTrue(WorkspaceStorage.deleteWorkspace(environments, selected, "debian-arm64"))
            assertFalse(selected.exists())
            assertEquals("untouched", File(sibling, "marker.txt").readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun refusesDeletionOutsideEnvironmentRoot() {
        val root = createTempDir(prefix = "pixeldesk-delete-")
        try {
            val environments = File(root, "environments").apply { mkdir() }
            val outside = File(root, "outside").apply { mkdir() }
            try {
                WorkspaceStorage.deleteWorkspace(environments, outside, "outside")
                throw AssertionError("Outside workspace must not be deleted")
            } catch (_: IllegalArgumentException) { assertTrue(outside.exists()) }
        } finally { root.deleteRecursively() }
    }

    private fun state(workspace: EnvironmentWorkspace): EnvironmentState = when {
        !workspace.directory.exists() -> EnvironmentState.INVALID
        !workspace.rootFilesystem.exists() -> EnvironmentState.STORAGE_ONLY
        runCatching { RootfsValidator.validate(workspace.rootFilesystem) }.isSuccess -> EnvironmentState.INSTALLED
        else -> EnvironmentState.INVALID
    }

    private fun workspace(directory: File) = EnvironmentWorkspace(
        id = "debian-arm64", displayName = "Debian ARM64", directory = directory,
        rootFilesystem = File(directory, "rootfs"), home = File(directory, "home"),
        downloads = File(directory, "downloads"), temporary = File(directory, "tmp")
    )
}
