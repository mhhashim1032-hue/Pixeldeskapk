package com.pixeldesk.app

import java.io.File

data class WorkspaceEntry(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long
)

/** Sandboxed file operations for a workspace; every resolved path stays under its root. */
object WorkspaceFiles {
    private const val MAX_TEXT_BYTES = 256 * 1024L

    fun list(root: File, relativePath: String = ""): List<WorkspaceEntry> {
        val directory = resolve(root, relativePath)
        require(directory.isDirectory) { "Selected path is not a directory." }
        val canonicalRoot = root.canonicalFile
        return directory.listFiles()
            ?.filter { file ->
                file.canonicalFile.path.startsWith(canonicalRoot.path + File.separator)
            }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?.map { file ->
                WorkspaceEntry(
                    name = file.name,
                    relativePath = root.canonicalFile.toPath()
                        .relativize(file.canonicalFile.toPath()).toString(),
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isFile) file.length() else 0L
                )
            }.orEmpty()
    }

    fun createDirectory(root: File, parentPath: String, name: String): File {
        validateName(name)
        val parent = resolve(root, parentPath)
        require(parent.isDirectory) { "Parent directory does not exist." }
        val target = resolve(root, join(parentPath, name))
        check(!target.exists()) { "An item with that name already exists." }
        check(target.mkdir()) { "Could not create directory." }
        return target
    }

    fun createTextFile(root: File, parentPath: String, name: String, contents: String = ""): File {
        validateName(name)
        val parent = resolve(root, parentPath)
        require(parent.isDirectory) { "Parent directory does not exist." }
        val target = resolve(root, join(parentPath, name))
        check(!target.exists()) { "An item with that name already exists." }
        check(target.createNewFile()) { "Could not create file." }
        target.writeText(contents)
        return target
    }

    fun readText(root: File, relativePath: String): String {
        val file = resolve(root, relativePath)
        require(file.isFile) { "Selected item is not a file." }
        require(file.length() <= MAX_TEXT_BYTES) { "File is too large to preview." }
        return file.readText()
    }

    /** Deletes files and empty directories only; non-empty folders are preserved. */
    fun delete(root: File, relativePath: String) {
        require(relativePath.isNotBlank()) { "Workspace root cannot be deleted." }
        val target = resolve(root, relativePath)
        check(target.exists()) { "Item does not exist." }
        check(target.delete()) { "Could not delete item. Folders must be empty." }
    }

    private fun resolve(root: File, relativePath: String): File {
        val canonicalRoot = root.canonicalFile
        check(canonicalRoot.exists() && canonicalRoot.isDirectory) { "Workspace root is unavailable." }
        require(relativePath.indexOf(0.toChar()) < 0) { "Invalid path." }
        val target = if (relativePath.isBlank()) canonicalRoot else File(canonicalRoot, relativePath)
        val canonicalTarget = target.canonicalFile
        val rootPath = canonicalRoot.path
        check(canonicalTarget.path == rootPath ||
            canonicalTarget.path.startsWith(rootPath + File.separator)) {
            "Path is outside the workspace."
        }
        return canonicalTarget
    }

    private fun validateName(name: String) {
        require(name.isNotBlank() && name != "." && name != "..") { "Enter a valid name." }
        require(name.length <= 100 && !name.contains('/') && !name.contains('\\') &&
            name.indexOf(0.toChar()) < 0) { "Names cannot contain path separators." }
    }

    private fun join(parent: String, name: String): String =
        if (parent.isBlank()) name else "$parent${File.separator}$name"
}
