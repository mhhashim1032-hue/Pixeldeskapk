package com.pixeldesk.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentBackupExporterTest {
    @Test
    fun exportsWorkspaceContentsAndCompressedArchiveChecksum() {
        val fixture = fixture()
        try {
            File(fixture.workspace.rootFilesystem, "etc/os-release")
                .writeText("ID=debian\nVERSION_ID=12\n")
            File(fixture.workspace.home, "notes.txt").writeText("my local files")

            val destination = ByteArrayOutputStream()
            val result = EnvironmentBackupExporter.export(fixture.workspace, destination)
            val archiveBytes = destination.toByteArray()
            val expectedSha = MessageDigest.getInstance("SHA-256").digest(archiveBytes)
                .joinToString("") { "%02x".format(it) }

            assertTrue(archiveBytes.isNotEmpty())
            assertEquals(expectedSha, result.sha256)
            assertTrue(result.uncompressedBytes >= "ID=debian\nVERSION_ID=12\n".toByteArray().size)
            assertTrue(result.entryCount > 4)

            val files = linkedMapOf<String, ByteArray>()
            val symbolicLinks = linkedMapOf<String, String>()
            TarArchiveInputStream(
                GzipCompressorInputStream(ByteArrayInputStream(archiveBytes))
            ).use { input ->
                while (true) {
                    val entry = input.nextTarEntry ?: break
                    if (entry.isSymbolicLink) {
                        symbolicLinks[entry.name] = entry.linkName
                    } else {
                        files[entry.name] = input.readBytes()
                    }
                }
            }

            assertTrue(files.containsKey("debian-arm64/rootfs/etc/os-release"))
            assertArrayEquals(
                "my local files".toByteArray(),
                files["debian-arm64/home/notes.txt"]
            )
            assertTrue(files.containsKey("PIXELDESK_BACKUP.txt"))
            assertTrue(files["PIXELDESK_BACKUP.txt"]!!.toString(Charsets.UTF_8).contains("format_version=1"))
            assertEquals(result.entryCount, files.size + symbolicLinks.size)
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun archivesSymbolicLinksWithoutFollowingThem() {
        val fixture = fixture()
        try {
            val link = File(fixture.workspace.rootFilesystem, "bin")
            Files.createSymbolicLink(link.toPath(), Paths.get("usr/bin"))
            val absoluteLink = File(fixture.workspace.rootFilesystem, "lib")
            Files.createSymbolicLink(absoluteLink.toPath(), Paths.get("/usr/lib"))

            val destination = ByteArrayOutputStream()
            EnvironmentBackupExporter.export(fixture.workspace, destination)

            val links = mutableMapOf<String, String>()
            TarArchiveInputStream(
                GzipCompressorInputStream(ByteArrayInputStream(destination.toByteArray()))
            ).use { input ->
                while (true) {
                    val entry = input.nextTarEntry ?: break
                    if (entry.isSymbolicLink) links[entry.name] = entry.linkName
                    else input.readBytes()
                }
            }

            assertEquals("usr/bin", links["debian-arm64/rootfs/bin"])
            assertEquals("/usr/lib", links["debian-arm64/rootfs/lib"])
        } finally {
            // File.deleteRecursively follows FileTreeWalk semantics and is not
            // safe for an absolute symlink in this fixture. Remove both links
            // explicitly before recursively deleting the temporary workspace.
            Files.deleteIfExists(File(fixture.workspace.rootFilesystem, "bin").toPath())
            Files.deleteIfExists(File(fixture.workspace.rootFilesystem, "lib").toPath())
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun rejectsRelativeSymbolicLinksThatEscapeWorkspace() {
        val fixture = fixture()
        try {
            Files.createSymbolicLink(
                File(fixture.workspace.rootFilesystem, "escape").toPath(),
                Paths.get("../../../../outside")
            )
            val failure = runCatching {
                EnvironmentBackupExporter.export(fixture.workspace, ByteArrayOutputStream())
            }
            assertTrue(failure.isFailure)
            assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("escape"))
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun rejectsInvalidEnvironmentIdentifier() {
        val fixture = fixture()
        try {
            val invalid = fixture.workspace.copy(id = "../outside")
            val failure = runCatching {
                EnvironmentBackupExporter.export(invalid, ByteArrayOutputStream())
            }
            assertTrue(failure.isFailure)
            assertFalse(failure.isSuccess)
        } finally {
            fixture.root.deleteRecursively()
        }
    }

    private data class Fixture(val root: File, val workspace: EnvironmentWorkspace)

    private fun fixture(): Fixture {
        val root = Files.createTempDirectory("pixeldesk-backup").toFile()
        val workspaceDirectory = File(root, "debian-arm64").apply { mkdirs() }
        val rootfs = File(workspaceDirectory, "rootfs").apply { mkdirs() }
        val home = File(workspaceDirectory, "home").apply { mkdirs() }
        val downloads = File(workspaceDirectory, "downloads").apply { mkdirs() }
        val temporary = File(workspaceDirectory, "tmp").apply { mkdirs() }
        File(rootfs, "etc").mkdirs()
        File(rootfs, "usr/bin").mkdirs()
        return Fixture(
            root,
            EnvironmentWorkspace(
                id = "debian-arm64",
                displayName = "Debian ARM64",
                directory = workspaceDirectory,
                rootFilesystem = rootfs,
                home = home,
                downloads = downloads,
                temporary = temporary
            )
        )
    }
}
