package com.pixeldesk.app

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

data class RootfsExtractionLimits(
    val maxEntries: Int = 250_000,
    val maxExpandedBytes: Long = 4L * 1024 * 1024 * 1024,
    val maxSingleFileBytes: Long = 512L * 1024 * 1024
) {
    init {
        require(maxEntries > 0)
        require(maxExpandedBytes > 0)
        require(maxSingleFileBytes > 0)
        require(maxSingleFileBytes <= maxExpandedBytes)
    }
}

data class RootfsExtractionResult(
    val destination: File,
    val entriesExtracted: Int,
    val bytesExtracted: Long
)

/**
 * Extracts a gzip-compressed TAR into a new directory.
 *
 * Extraction is staged and only promoted when every entry has been checked.
 * Absolute paths, parent traversal, escaping symlinks, hard links and special
 * filesystem nodes are rejected. Ownership, setuid/setgid bits and device
 * nodes are never restored.
 */
object SafeRootfsExtractor {
    fun extractTarGz(
        archive: File,
        destination: File,
        limits: RootfsExtractionLimits = RootfsExtractionLimits()
    ): RootfsExtractionResult {
        require(archive.isFile) { "Root filesystem archive does not exist." }
        val finalPath = destination.absoluteFile.toPath().normalize()
        val parent = finalPath.parent ?: error("Destination must have a parent directory.")
        Files.createDirectories(parent)
        require(!Files.exists(finalPath)) { "Extraction destination already exists." }

        val staging = parent.resolve(finalPath.fileName.toString() + ".extracting")
        require(!Files.exists(staging)) { "An extraction is already in progress." }
        Files.createDirectory(staging)

        var entryCount = 0
        var expandedBytes = 0L
        val root = staging.toRealPath()

        try {
            TarArchiveInputStream(
                GzipCompressorInputStream(BufferedInputStream(FileInputStream(archive)))
            ).use { tar ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    entryCount++
                    require(entryCount <= limits.maxEntries) {
                        "Archive contains too many entries."
                    }

                    if (entry.name == "." || entry.name == "./") {
                        require(entry.isDirectory) { "Archive root entry must be a directory." }
                        continue
                    }
                    val target = resolveEntry(root, entry.name)
                    ensureSafeParent(root, target)
                    require(!Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                        "Archive contains a duplicate or conflicting path: ${entry.name}"
                    }

                    when {
                        entry.isDirectory -> {
                            require(entry.size == 0L) { "Directory entry has unexpected data." }
                            Files.createDirectories(target)
                        }
                        entry.isSymbolicLink -> {
                            require(entry.size == 0L) { "Symbolic link entry has unexpected data." }
                            val linkText = entry.linkName
                            val suppliedLink = Paths.get(linkText)
                            val resolvedLink = if (suppliedLink.isAbsolute) {
                                root.resolve(suppliedLink.toString().trimStart('/')).normalize()
                            } else {
                                target.parent.resolve(suppliedLink).normalize()
                            }
                            require(resolvedLink.startsWith(root)) {
                                "Archive contains a symbolic link outside the root filesystem."
                            }
                            val safeLink = target.parent.relativize(resolvedLink)
                            Files.createSymbolicLink(target, safeLink)
                        }
                        entry.isLink -> {
                            val source = resolveHardLinkTarget(root, entry.linkName)
                            require(Files.isRegularFile(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                                "Hard-link target is missing or is not a regular file: ${entry.linkName}"
                            }
                            require(source.toRealPath().startsWith(root)) {
                                "Hard-link target resolves outside the root filesystem."
                            }
                            val linkedBytes = Files.size(source)
                            require(linkedBytes <= limits.maxSingleFileBytes) {
                                "Hard-link target exceeds the per-file size limit."
                            }
                            expandedBytes = Math.addExact(expandedBytes, linkedBytes)
                            require(expandedBytes <= limits.maxExpandedBytes) {
                                "Archive exceeds the expanded size limit."
                            }
                            Files.copy(source, target)
                            if (source.toFile().canExecute()) {
                                target.toFile().setExecutable(true, false)
                            }
                        }
                        entry.isCharacterDevice || entry.isBlockDevice || entry.isFIFO ->
                            error("Special filesystem nodes are not allowed in rootfs archives.")
                        entry.isFile -> {
                            require(entry.size >= 0L && entry.size <= limits.maxSingleFileBytes) {
                                "Archive contains a file exceeding the per-file size limit."
                            }
                            expandedBytes = Math.addExact(expandedBytes, entry.size)
                            require(expandedBytes <= limits.maxExpandedBytes) {
                                "Archive exceeds the expanded size limit."
                            }
                            Files.createDirectories(target.parent)
                            FileOutputStream(target.toFile()).use { output ->
                                var copied = 0L
                                while (true) {
                                    val count = tar.read(buffer)
                                    if (count < 0) break
                                    copied += count
                                    require(copied <= entry.size) {
                                        "Archive entry exceeded its declared size."
                                    }
                                    output.write(buffer, 0, count)
                                }
                                require(copied == entry.size) {
                                    "Archive entry was truncated: ${entry.name}"
                                }
                                output.fd.sync()
                            }
                            if (entry.mode and 0b001001001 != 0) {
                                target.toFile().setExecutable(true, false)
                            }
                        }
                        else -> error("Unsupported TAR entry type: ${entry.name}")
                    }
                }
            }

            require(Files.move(staging, finalPath, StandardCopyOption.ATOMIC_MOVE) != null) {
                "Could not finalize extracted root filesystem."
            }
            return RootfsExtractionResult(finalPath.toFile(), entryCount, expandedBytes)
        } catch (error: Throwable) {
            staging.toFile().deleteRecursively()
            throw error
        }
    }

    private fun resolveEntry(root: Path, entryName: String): Path {
        require(entryName.isNotBlank()) { "Archive contains an empty path." }
        val relative = Paths.get(entryName)
        require(!relative.isAbsolute) { "Absolute archive paths are not allowed." }
        require(relative.none { it.toString() == ".." }) {
            "Archive path traversal is not allowed."
        }
        val target = root.resolve(relative).normalize()
        require(target != root && target.startsWith(root)) {
            "Archive entry escapes the extraction directory."
        }
        return target
    }

    private fun resolveHardLinkTarget(root: Path, linkName: String): Path {
        require(linkName.isNotBlank()) { "Archive contains an empty hard-link target." }
        val supplied = Paths.get(linkName)
        val target = if (supplied.isAbsolute) {
            root.resolve(supplied.toString().trimStart('/')).normalize()
        } else {
            root.resolve(supplied).normalize()
        }
        require(target != root && target.startsWith(root)) {
            "Hard-link target escapes the extraction directory."
        }
        require(!target.any { it.toString() == ".." }) {
            "Hard-link path traversal is not allowed."
        }
        require(Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            "Hard-link target must appear earlier in the archive."
        }
        return target
    }
    private fun ensureSafeParent(root: Path, target: Path) {
        var parent = target.parent ?: error("Archive entry has no parent.")
        val ancestors = mutableListOf<Path>()
        while (parent != root && parent.startsWith(root)) {
            ancestors.add(parent)
            parent = parent.parent ?: break
        }
        require(parent == root) { "Archive path escapes the extraction directory." }

        ancestors.asReversed().forEach { directory ->
            if (Files.exists(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                require(Files.isDirectory(directory)) { "Archive path parent is not a directory." }
                require(directory.toRealPath().startsWith(root)) {
                    "Archive path resolves outside the extraction directory."
                }
            } else {
                Files.createDirectory(directory)
            }
        }
    }
}
