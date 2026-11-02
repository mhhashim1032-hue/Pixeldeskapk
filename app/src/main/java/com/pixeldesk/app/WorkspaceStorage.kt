package com.pixeldesk.app

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * Creates the app-private directory layout used by a future Linux userspace.
 *
 * This deliberately does not claim to install a distribution or provide a
 * Linux kernel. It only prepares durable, sandboxed storage for later runtime
 * adapters.
 */
data class EnvironmentWorkspace(
    val id: String,
    val displayName: String,
    val directory: File,
    val rootFilesystem: File,
    val home: File,
    val downloads: File,
    val temporary: File
)

object WorkspaceStorage {
    private val validId = Regex("[a-z0-9][a-z0-9-]{0,39}")

    fun create(context: Context, displayName: String): EnvironmentWorkspace {
        val normalizedName = displayName.trim()
        require(normalizedName.isNotEmpty()) { "Environment name cannot be empty." }

        val id = normalizedName
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .trim('-')

        require(validId.matches(id)) { "Environment name must contain letters or numbers." }

        val base = File(context.filesDir, "environments")
        val workspace = File(base, id)
        check(workspace.canonicalFile.parentFile == base.canonicalFile) {
            "Invalid environment directory."
        }
        check(workspace.exists() || workspace.mkdirs()) {
            "Unable to create environment directory."
        }

        val rootfs = File(workspace, "rootfs")
        val home = File(workspace, "home")
        val downloads = File(workspace, "downloads")
        val temporary = File(workspace, "tmp")

        listOf(rootfs, home, downloads, temporary).forEach { directory ->
            check(directory.exists() || directory.mkdirs()) {
                "Unable to prepare ${directory.name} directory."
            }
        }

        return EnvironmentWorkspace(
            id = id,
            displayName = normalizedName,
            directory = workspace,
            rootFilesystem = rootfs,
            home = home,
            downloads = downloads,
            temporary = temporary
        )
    }

    fun list(context: Context): List<EnvironmentWorkspace> {
        val base = File(context.filesDir, "environments")
        val directories = base.listFiles()
            ?.filter { it.isDirectory && !Files.isSymbolicLink(it.toPath()) && validId.matches(it.name) }
            .orEmpty()

        return directories.map { directory ->
            EnvironmentWorkspace(
                id = directory.name,
                displayName = directory.name.replace('-', ' ').replaceFirstChar { it.uppercase() },
                directory = directory,
                rootFilesystem = File(directory, "rootfs"),
                home = File(directory, "home"),
                downloads = File(directory, "downloads"),
                temporary = File(directory, "tmp")
            )
        }.sortedBy { it.displayName.lowercase(Locale.ROOT) }
    }
}
