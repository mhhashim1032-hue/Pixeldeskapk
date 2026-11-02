package com.pixeldesk.app

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeRootfsExtractorTest {
    @Test
    fun extractsRegularFilesAndKeepsAbsoluteSymlinksInsideRoot() {
        val base = Files.createTempDirectory("pixeldesk-rootfs-test").toFile()
        try {
            val archive = createArchive(base, listOf(
                Entry("usr/bin/tool", "binary".toByteArray()),
                Entry("bin", link = "/usr/bin", type = TarArchiveEntry.LF_SYMLINK)
            ))
            val destination = File(base, "rootfs")

            val result = SafeRootfsExtractor.extractTarGz(archive, destination)

            assertEquals(2, result.entriesExtracted)
            assertEquals("binary", File(destination, "usr/bin/tool").readText())
            assertTrue(Files.isSymbolicLink(File(destination, "bin").toPath()))
            assertEquals(File(destination, "usr/bin").canonicalFile, File(destination, "bin").canonicalFile)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun rejectsParentTraversalAndCleansStagingDirectory() {
        val base = Files.createTempDirectory("pixeldesk-rootfs-traversal").toFile()
        try {
            val archive = createArchive(base, listOf(Entry("../escape.txt", "bad".toByteArray())))
            val destination = File(base, "rootfs")

            runCatching { SafeRootfsExtractor.extractTarGz(archive, destination) }
                .onSuccess { error("Traversal archive should have been rejected.") }

            assertFalse(File(base, "escape.txt").exists())
            assertFalse(destination.exists())
            assertFalse(File(base, "rootfs.extracting").exists())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymlinksThatEscapeRoot() {
        val base = Files.createTempDirectory("pixeldesk-rootfs-link").toFile()
        try {
            val archive = createArchive(base, listOf(
                Entry("escape", link = "../../../../outside", type = TarArchiveEntry.LF_SYMLINK)
            ))
            val destination = File(base, "rootfs")

            runCatching { SafeRootfsExtractor.extractTarGz(archive, destination) }
                .onSuccess { error("Escaping symlink should have been rejected.") }

            assertFalse(destination.exists())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun enforcesExpandedSizeLimit() {
        val base = Files.createTempDirectory("pixeldesk-rootfs-size").toFile()
        try {
            val archive = createArchive(base, listOf(Entry("large.bin", ByteArray(32))))
            val destination = File(base, "rootfs")

            runCatching {
                SafeRootfsExtractor.extractTarGz(
                    archive,
                    destination,
                    RootfsExtractionLimits(maxExpandedBytes = 16, maxSingleFileBytes = 16)
                )
            }.onSuccess { error("Oversized archive should have been rejected.") }

            assertFalse(destination.exists())
        } finally {
            base.deleteRecursively()
        }
    }


    @Test
    fun materializesHardLinksAsIndependentCopies() {
        val base = Files.createTempDirectory("pixeldesk-rootfs-hardlink").toFile()
        try {
            val archive = createArchive(base, listOf(
                Entry("usr/lib/libsample.so", "library".toByteArray()),
                Entry("usr/lib/libsample.so.1", link = "/usr/lib/libsample.so", type = TarArchiveEntry.LF_LINK)
            ))
            val destination = File(base, "rootfs")

            val result = SafeRootfsExtractor.extractTarGz(archive, destination)
            val original = File(destination, "usr/lib/libsample.so").toPath()
            val copy = File(destination, "usr/lib/libsample.so.1").toPath()

            assertEquals(2, result.entriesExtracted)
            assertEquals("library", copy.toFile().readText())
            assertFalse(Files.isSameFile(original, copy))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun rejectsHardLinkTraversalTargets() {
        val base = Files.createTempDirectory("pixeldesk-rootfs-hardlink-traversal").toFile()
        try {
            val archive = createArchive(base, listOf(
                Entry("safe.txt", "safe".toByteArray()),
                Entry("escape.txt", link = "../outside.txt", type = TarArchiveEntry.LF_LINK)
            ))
            val destination = File(base, "rootfs")

            runCatching { SafeRootfsExtractor.extractTarGz(archive, destination) }
                .onSuccess { error("Hard-link traversal should have been rejected.") }

            assertFalse(destination.exists())
            assertFalse(File(base, "outside.txt").exists())
        } finally {
            base.deleteRecursively()
        }
    }

    private data class Entry(
        val name: String,
        val bytes: ByteArray = byteArrayOf(),
        val link: String? = null,
        val type: Byte = TarArchiveEntry.LF_NORMAL
    )

    private fun createArchive(base: File, entries: List<Entry>): File {
        val archive = File(base, "rootfs.tar.gz")
        TarArchiveOutputStream(GzipCompressorOutputStream(FileOutputStream(archive))).use { tar ->
            entries.forEach { item ->
                val entry = TarArchiveEntry(item.name, item.type)
                if (item.link != null) entry.linkName = item.link
                if (item.type == TarArchiveEntry.LF_NORMAL) entry.size = item.bytes.size.toLong()
                tar.putArchiveEntry(entry)
                if (item.bytes.isNotEmpty()) tar.write(item.bytes)
                tar.closeArchiveEntry()
            }
            tar.finish()
        }
        return archive
    }
}
