package com.pixeldesk.app

import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RootfsArtifact(
    val id: String,
    val displayName: String,
    val release: String,
    val architecture: String,
    val url: String,
    val sha256: String,
    val maxDownloadBytes: Long,
    val maxExpandedBytes: Long
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9-]{0,63}")))
        require(url.startsWith("https://", ignoreCase = true))
        require(sha256.matches(Regex("[a-fA-F0-9]{64}")))
        require(maxDownloadBytes > 0)
        require(maxExpandedBytes > 0)
    }
}

object RootfsCatalog {
    /**
     * Debian 12 Bookworm ARM64 base layer, pinned to the official
     * debuerreotype/docker-debian-artifacts source commit.
     */
    val debianBookwormArm64 = RootfsArtifact(
        id = "debian-bookworm-arm64",
        displayName = "Debian 12 Bookworm",
        release = "12",
        architecture = "arm64",
        url = "https://raw.githubusercontent.com/debuerreotype/docker-debian-artifacts/e36653f9967231373aeba7cff2102fd004ac4f4a/bookworm/oci/blobs/rootfs.tar.gz",
        sha256 = "9e2782a4701b518d11b63dd3dedcfe1338e448f72cbf5353e854f2501ffd5e62",
        maxDownloadBytes = 80L * 1024 * 1024,
        maxExpandedBytes = 2L * 1024 * 1024 * 1024
    )
}

data class RootfsValidation(
    val distribution: String,
    val release: String,
    val architecture: String,
    val shellPath: String
)

object RootfsValidator {
    private const val ELF_CLASS_64 = 2
    private const val ELF_DATA_LITTLE_ENDIAN = 1
    private const val EM_AARCH64 = 183

    fun validate(rootfs: File, expected: RootfsArtifact = RootfsCatalog.debianBookwormArm64): RootfsValidation {
        require(rootfs.isDirectory && !Files.isSymbolicLink(rootfs.toPath())) {
            "Root filesystem directory is missing or unsafe."
        }
        val root = rootfs.canonicalFile.toPath()

        val osRelease = File(rootfs, "etc/os-release")
        require(osRelease.isFile && osRelease.canRead()) { "Root filesystem is missing /etc/os-release." }
        val metadata = parseOsRelease(osRelease.readLines())
        val id = metadata["ID"]?.trim('"', '\'')?.lowercase()
            ?: error("Root filesystem os-release has no ID.")
        val version = metadata["VERSION_ID"]?.trim('"', '\'')
            ?: error("Root filesystem os-release has no VERSION_ID.")
        require(expected.architecture == "arm64") { "This validator currently supports ARM64 only." }
        require(id == "debian") { "Expected Debian rootfs; found ${id ?: "unknown"}." }
        require(version == expected.release) {
            "Expected Debian ${expected.release}; found ${version ?: "unknown"}."
        }

        val shellCandidates = listOf("usr/bin/dash", "usr/bin/bash", "bin/sh", "bin/bash")
        val shellRelative = shellCandidates.firstOrNull { relative ->
            val file = File(rootfs, relative)
            file.isFile && file.canRead()
        } ?: error("Root filesystem does not contain a readable POSIX shell.")

        val shell = File(rootfs, shellRelative).canonicalFile.toPath()
        require(shell.startsWith(root)) { "Root filesystem shell resolves outside the rootfs." }
        verifyAarch64Elf(shell.toFile())

        require(File(rootfs, "usr/bin/dpkg").isFile) {
            "Debian package manager is missing from the root filesystem."
        }

        return RootfsValidation(
            distribution = id,
            release = version,
            architecture = expected.architecture,
            shellPath = "/$shellRelative"
        )
    }

    private fun parseOsRelease(lines: List<String>): Map<String, String> =
        lines.mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null
            else line.substring(0, separator) to line.substring(separator + 1)
        }.toMap()

    private fun verifyAarch64Elf(file: File) {
        val header = ByteArray(20)
        FileInputStream(file).use { input ->
            var offset = 0
            while (offset < header.size) {
                val count = input.read(header, offset, header.size - offset)
                require(count > 0) { "Shell executable has a truncated ELF header." }
                offset += count
            }
        }
        require(header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()
        ) { "Root filesystem shell is not an ELF executable." }
        require((header[4].toInt() and 0xff) == ELF_CLASS_64) {
            "Root filesystem shell is not a 64-bit executable."
        }
        require((header[5].toInt() and 0xff) == ELF_DATA_LITTLE_ENDIAN) {
            "Root filesystem shell has an unsupported ELF byte order."
        }
        val machine = (header[18].toInt() and 0xff) or
            ((header[19].toInt() and 0xff) shl 8)
        require(machine == EM_AARCH64) {
            "Root filesystem shell is not ARM64 (ELF machine $machine)."
        }
    }
}

data class RootfsInstallResult(
    val artifact: RootfsArtifact,
    val validation: RootfsValidation,
    val downloadBytes: Long,
    val sha256: String,
    val extractedEntries: Int,
    val expandedBytes: Long
)

object RootfsInstaller {
    suspend fun install(
        workspace: EnvironmentWorkspace,
        artifact: RootfsArtifact = RootfsCatalog.debianBookwormArm64,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> }
    ): RootfsInstallResult = withContext(Dispatchers.IO) {
        val workspaceRoot = workspace.directory.canonicalFile
        require(workspace.rootFilesystem.canonicalFile.parentFile == workspaceRoot &&
            !Files.isSymbolicLink(workspace.rootFilesystem.toPath())
        ) {
            "Root filesystem destination is outside the environment workspace or is a symlink."
        }
        require(workspace.temporary.canonicalFile.parentFile == workspaceRoot &&
            !Files.isSymbolicLink(workspace.temporary.toPath())
        ) {
            "Temporary directory is outside the environment workspace or is a symlink."
        }
        require(workspace.temporary.isDirectory) { "Workspace temporary directory is missing." }

        val rootfs = workspace.rootFilesystem
        if (rootfs.exists()) {
            require(rootfs.isDirectory && rootfs.list()?.isEmpty() == true) {
                "An existing root filesystem will not be overwritten."
            }
            require(rootfs.delete()) { "Could not prepare root filesystem destination." }
        }

        val archive = File(workspace.temporary, "${artifact.id}.tar.gz").canonicalFile
        require(archive.parentFile == workspace.temporary.canonicalFile) {
            "Archive path is outside the workspace temporary directory."
        }
        archive.delete()

        try {
            val download = VerifiedDownloader.download(
                VerifiedDownloadSpec(
                    url = artifact.url,
                    sha256 = artifact.sha256,
                    maxBytes = artifact.maxDownloadBytes
                ),
                archive,
                onProgress
            )

            val extraction = SafeRootfsExtractor.extractTarGz(
                archive = download.file,
                destination = rootfs,
                limits = RootfsExtractionLimits(
                    maxExpandedBytes = artifact.maxExpandedBytes,
                    maxSingleFileBytes = 512L * 1024 * 1024
                )
            )
            val validation = RootfsValidator.validate(rootfs, artifact)

            RootfsInstallResult(
                artifact = artifact,
                validation = validation,
                downloadBytes = download.bytesWritten,
                sha256 = download.sha256,
                extractedEntries = extraction.entriesExtracted,
                expandedBytes = extraction.bytesExtracted
            )
        } catch (error: Throwable) {
            rootfs.deleteRecursively()
            throw error
        } finally {
            archive.delete()
        }
    }
}
