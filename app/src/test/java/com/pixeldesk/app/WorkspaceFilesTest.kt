package com.pixeldesk.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFilesTest {
    @Test
    fun createsListsAndReadsWorkspaceFiles() {
        val root = createTempDir(prefix = "pixeldesk-workspace-")
        try {
            WorkspaceFiles.createDirectory(root, "", "Documents")
            WorkspaceFiles.createTextFile(root, "Documents", "hello.txt", "Hello Pixel Desk")

            val entries = WorkspaceFiles.list(root)
            assertEquals("Documents", entries.single().name)
            assertTrue(entries.single().isDirectory)
            assertEquals("Hello Pixel Desk", WorkspaceFiles.readText(root, "Documents/hello.txt"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsTraversalOutsideWorkspace() {
        val parent = createTempDir(prefix = "pixeldesk-parent-")
        val root = File(parent, "workspace").apply { mkdir() }
        try {
            try {
                WorkspaceFiles.createTextFile(root, "", "../outside.txt", "no")
                throw AssertionError("Traversal should be rejected")
            } catch (_: IllegalArgumentException) {
                assertFalse(File(parent, "outside.txt").exists())
            }
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun refusesToDeleteWorkspaceRootAndNonEmptyDirectories() {
        val root = createTempDir(prefix = "pixeldesk-workspace-")
        try {
            WorkspaceFiles.createDirectory(root, "", "keep")
            WorkspaceFiles.createTextFile(root, "keep", "file.txt", "data")

            try {
                WorkspaceFiles.delete(root, "")
                throw AssertionError("Workspace root must not be deletable")
            } catch (_: IllegalArgumentException) {
                assertTrue(root.exists())
            }
            try {
                WorkspaceFiles.delete(root, "keep")
                throw AssertionError("Non-empty directory must not be deletable")
            } catch (_: IllegalStateException) {
                assertTrue(File(root, "keep/file.txt").exists())
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
