package com.pixeldesk.app

import java.io.File
import java.io.InputStream

/** Result for a successful import into a workspace directory. */
data class ImportedWorkspaceFile(val file: File, val bytesWritten: Long)

/**
 * Streaming import helper. It never overwrites an existing file and only
 * promotes a temporary file after the complete stream has been written.
 */
object WorkspaceImport {
    fun copyStream(
        root: File,
        parentPath: String,
        displayName: String,
        input: InputStream,
        maxBytes: Long = 512L * 1024L * 1024L
    ): ImportedWorkspaceFile {
        require(maxBytes > 0L) { "Import size limit must be positive." }
        val safeName = sanitizeName(displayName)
        val parent = resolveParent(root, parentPath)
        val destination = File(parent, safeName)
        check(!destination.exists()) { "A file with that name already exists." }
        val partial = File(parent, safeName + ".importing")
        check(!partial.exists()) { "A matching import is already in progress." }

        var written = 0L
        try {
            input.use { source ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        written += count
                        require(written <= maxBytes) { "Selected file exceeds the import size limit." }
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
            check(partial.renameTo(destination)) { "Could not finalize imported file." }
            return ImportedWorkspaceFile(destination, written)
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    fun sanitizeName(displayName: String): String {
        val leaf = displayName.substringAfterLast('/').substringAfterLast('\\')
        val safe = leaf.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().trim('.')
        require(safe.isNotBlank() && safe != "." && safe != "..") {
            "Selected file has an invalid name."
        }
        return safe.take(100)
    }

    private fun resolveParent(root: File, relativePath: String): File {
        val canonicalRoot = root.canonicalFile
        check(canonicalRoot.isDirectory) { "Workspace root is unavailable." }
        val parent = if (relativePath.isBlank()) canonicalRoot else File(canonicalRoot, relativePath).canonicalFile
        require(parent.path == canonicalRoot.path ||
            parent.path.startsWith(canonicalRoot.path + File.separator)) {
            "Import destination is outside the workspace."
        }
        require(parent.isDirectory) { "Import destination does not exist." }
        return parent
    }
}
