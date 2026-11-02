package com.pixeldesk.app

import java.io.BufferedOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.Date
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream

data class EnvironmentBackupResult(
    val entryCount: Int,
    val uncompressedBytes: Long,
    val sha256: String
)

/**
 * Streams an idle environment into a portable TAR.GZ backup.
 *
 * The destination stream remains owned by the caller. Symbolic links are
 * archived as links (not followed), and relative links that escape the
 * workspace are rejected. The SHA-256 digest covers the complete compressed
 * TAR.GZ byte stream.
 */
object EnvironmentBackupExporter {
    private val validId = Regex("[a-z0-9][a-z0-9-]{0,39}")
    private const val MAX_ENTRIES = 250_000
    private const val MAX_UNCOMPRESSED_BYTES = 32L * 1024L * 1024L * 1024L
    private const val BUFFER_SIZE = 64 * 1024

    fun export(
        workspace: EnvironmentWorkspace,
        destination: OutputStream
    ): EnvironmentBackupResult {
        require(validId.matches(workspace.id)) { "Invalid environment identifier." }
        check(!EnvironmentRuntimeManager.isRunning(workspace.id)) {
            "Stop the environment before creating a backup."
        }

        val directory = workspace.directory
        require(!Files.isSymbolicLink(directory.toPath()) && directory.isDirectory) {
            "The environment directory is missing or unsafe."
        }
        val parent = directory.parentFile
            ?: throw IllegalArgumentException("The environment directory has no parent.")
        require(!Files.isSymbolicLink(parent.toPath()) && parent.isDirectory) {
            "The environments directory is missing or unsafe."
        }
        val canonicalParent = parent.canonicalFile
        val canonicalDirectory = directory.canonicalFile
        require(
            canonicalDirectory.parentFile == canonicalParent &&
                canonicalDirectory.name == workspace.id
        ) { "Environment directory is outside its expected parent." }

        val root = canonicalDirectory.toPath()
        val digest = MessageDigest.getInstance("SHA-256")
        var entryCount = 0
        var uncompressedBytes = 0L

        fun registerEntry() {
            entryCount += 1
            if (entryCount > MAX_ENTRIES) {
                throw IOException("Backup contains too many filesystem entries.")
            }
        }

        fun archivePath(path: Path): String {
            val relative = root.relativize(path).toString()
                .replace(java.io.File.separatorChar, '/')
            return if (relative.isEmpty()) "${workspace.id}/" else "${workspace.id}/$relative"
        }

        fun addDirectory(archive: TarArchiveOutputStream, path: Path, attributes: BasicFileAttributes) {
            registerEntry()
            val name = archivePath(path).removeSuffix("/") + "/"
            val entry = TarArchiveEntry(name, TarConstants.LF_DIR)
            entry.mode = 493 // 0755
            entry.modTime = Date(attributes.lastModifiedTime().toMillis())
            archive.putArchiveEntry(entry)
            archive.closeArchiveEntry()
        }

        fun addSymbolicLink(
            archive: TarArchiveOutputStream,
            path: Path,
            attributes: BasicFileAttributes
        ) {
            val relative = root.relativize(path).toString()
                .replace(java.io.File.separatorChar, '/')
            val target = Files.readSymbolicLink(path).toString()
            validateSymbolicLinkTarget(relative, target)
            registerEntry()
            val entry = TarArchiveEntry(archivePath(path), TarConstants.LF_SYMLINK)
            entry.linkName = target
            entry.mode = 511 // 0777
            entry.modTime = Date(attributes.lastModifiedTime().toMillis())
            archive.putArchiveEntry(entry)
            archive.closeArchiveEntry()
        }

        fun addRegularFile(
            archive: TarArchiveOutputStream,
            path: Path,
            attributes: BasicFileAttributes
        ) {
            val declaredSize = attributes.size()
            if (declaredSize < 0 || declaredSize > MAX_UNCOMPRESSED_BYTES - uncompressedBytes) {
                throw IOException("Backup exceeds the 32 GiB uncompressed size limit.")
            }
            registerEntry()
            val entry = TarArchiveEntry(archivePath(path), TarConstants.LF_NORMAL)
            entry.size = declaredSize
            entry.mode = if (Files.isExecutable(path)) 493 else 420 // 0755 or 0644
            entry.modTime = Date(attributes.lastModifiedTime().toMillis())
            archive.putArchiveEntry(entry)

            var copied = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    if (copied > declaredSize || copied > MAX_UNCOMPRESSED_BYTES - uncompressedBytes) {
                        throw IOException("A file changed or exceeded the backup size limit during export.")
                    }
                    archive.write(buffer, 0, read)
                }
            }
            if (copied != declaredSize) {
                throw IOException("A file changed while the backup was being created.")
            }
            archive.closeArchiveEntry()
            uncompressedBytes += copied
        }

        // Do not close the caller-owned SAF output stream when the archive closes.
        val nonClosing = object : FilterOutputStream(destination) {
            override fun close() {
                flush()
            }
        }
        val digesting = DigestOutputStream(BufferedOutputStream(nonClosing), digest)

        TarArchiveOutputStream(GzipCompressorOutputStream(digesting)).use { archive ->
            archive.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            archive.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)

            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    directoryPath: Path,
                    attributes: BasicFileAttributes
                ): FileVisitResult {
                    if (attributes.isSymbolicLink || Files.isSymbolicLink(directoryPath)) {
                        throw IOException("A directory changed into a symbolic link during backup.")
                    }
                    addDirectory(archive, directoryPath, attributes)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    file: Path,
                    attributes: BasicFileAttributes
                ): FileVisitResult {
                    when {
                        attributes.isSymbolicLink || Files.isSymbolicLink(file) ->
                            addSymbolicLink(archive, file, attributes)
                        attributes.isRegularFile ->
                            addRegularFile(archive, file, attributes)
                        else ->
                            throw IOException("Unsupported special filesystem entry: ${root.relativize(file)}")
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, error: IOException): FileVisitResult {
                    throw error
                }
            })

            val displayName = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(workspace.displayName.toByteArray(StandardCharsets.UTF_8))
            val manifest = (
                "format=PIXEL_DESK_BACKUP\n" +
                    "format_version=1\n" +
                    "environment_id=${workspace.id}\n" +
                    "display_name_base64=$displayName\n" +
                    "created_at_epoch_ms=${System.currentTimeMillis()}\n" +
                    "archive_prefix=${workspace.id}/\n"
                ).toByteArray(StandardCharsets.UTF_8)
            registerEntry()
            val manifestEntry = TarArchiveEntry("PIXELDESK_BACKUP.txt", TarConstants.LF_NORMAL)
            manifestEntry.size = manifest.size.toLong()
            manifestEntry.mode = 420
            archive.putArchiveEntry(manifestEntry)
            archive.write(manifest)
            archive.closeArchiveEntry()
            archive.finish()
        }

        val sha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        return EnvironmentBackupResult(entryCount, uncompressedBytes, sha256)
    }

    internal fun validateSymbolicLinkTarget(relativePath: String, target: String) {
        require(target.isNotEmpty()) { "Symbolic link target cannot be empty." }
        val targetPath = Paths.get(target)
        if (targetPath.isAbsolute) {
            // Absolute Linux paths are interpreted from the guest root by the
            // rootfs extractor; normalize them to ensure they remain rooted.
            require(targetPath.normalize().isAbsolute) {
                "Absolute symbolic link target is invalid."
            }
            return
        }

        val linkPath = Paths.get(relativePath)
        val parent = linkPath.parent ?: Paths.get("")
        val resolved = parent.resolve(targetPath).normalize()
        require(!resolved.isAbsolute && !resolved.startsWith("..")) {
            "Symbolic link would escape the environment workspace."
        }
    }
}
