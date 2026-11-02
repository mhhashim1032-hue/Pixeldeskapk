package com.pixeldesk.app

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceImportTest {
    @Test
    fun importsContentWithSanitizedName() {
        val root = createTempDir(prefix = "pixeldesk-import-")
        try {
            val result = WorkspaceImport.copyStream(
                root = root,
                parentPath = "",
                displayName = "../report?.txt",
                input = ByteArrayInputStream("safe content".toByteArray())
            )
            assertEquals("report_.txt", result.file.name)
            assertEquals("safe content", result.file.readText())
            assertEquals(12L, result.bytesWritten)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsOversizedInputAndRemovesPartialFile() {
        val root = createTempDir(prefix = "pixeldesk-import-")
        try {
            try {
                WorkspaceImport.copyStream(
                    root = root,
                    parentPath = "",
                    displayName = "large.bin",
                    input = ByteArrayInputStream(ByteArray(16)),
                    maxBytes = 8L
                )
                throw AssertionError("Oversized file should be rejected")
            } catch (_: IllegalArgumentException) {
                assertFalse(File(root, "large.bin").exists())
                assertFalse(File(root, "large.bin.importing").exists())
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun refusesToOverwriteExistingFiles() {
        val root = createTempDir(prefix = "pixeldesk-import-")
        try {
            File(root, "existing.txt").writeText("original")
            try {
                WorkspaceImport.copyStream(
                    root, "", "existing.txt", ByteArrayInputStream("replacement".toByteArray())
                )
                throw AssertionError("Existing file should not be overwritten")
            } catch (_: IllegalStateException) {
                assertTrue(File(root, "existing.txt").readText() == "original")
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
