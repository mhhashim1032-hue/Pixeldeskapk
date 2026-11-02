package com.pixeldesk.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceStorageLifecycleTest {
    @Test
    fun deletesOnlyTheSelectedWorkspaceTree() {
        val base = createTempDir(prefix = "pixeldesk-environments-")
        val workspace = File(base, "debian-arm64").apply { mkdirs() }
        File(workspace, "rootfs/etc").apply { mkdirs() }
        File(workspace, "rootfs/etc/os-release").writeText("ID=debian")
        val sibling = File(base, "other").apply { mkdirs() }
        val siblingMarker = File(sibling, "keep.txt").apply { writeText("keep") }
        try {
            assertTrue(WorkspaceStorage.deleteWorkspace(base, workspace, "debian-arm64"))
            assertFalse(workspace.exists())
            assertTrue(siblingMarker.exists())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun refusesToDeleteOutsideEnvironmentDirectory() {
        val base = createTempDir(prefix = "pixeldesk-environments-")
        val outside = createTempDir(prefix = "pixeldesk-outside-")
        try {
            assertTrue(runCatching {
                WorkspaceStorage.deleteWorkspace(base, outside, "debian-arm64")
            }.isFailure)
            assertTrue(outside.exists())
        } finally {
            base.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun unlinksSymlinkWithoutDeletingItsTarget() {
        val base = createTempDir(prefix = "pixeldesk-environments-")
        val workspace = File(base, "debian-arm64").apply { mkdirs() }
        val outside = createTempDir(prefix = "pixeldesk-linked-target-")
        val marker = File(outside, "keep.txt").apply { writeText("keep") }
        try {
            Files.createSymbolicLink(File(workspace, "external").toPath(), outside.toPath())
            assertTrue(WorkspaceStorage.deleteWorkspace(base, workspace, "debian-arm64"))
            assertTrue(marker.exists())
        } finally {
            base.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun rejectsInvalidEnvironmentIds() {
        val base = createTempDir(prefix = "pixeldesk-environments-")
        val workspace = File(base, "bad name").apply { mkdirs() }
        try {
            assertTrue(runCatching {
                WorkspaceStorage.deleteWorkspace(base, workspace, "bad name")
            }.isFailure)
            assertTrue(workspace.exists())
        } finally {
            base.deleteRecursively()
        }
    }
}
