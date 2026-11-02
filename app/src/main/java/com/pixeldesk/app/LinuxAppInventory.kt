package com.pixeldesk.app

internal data class LinuxDesktopApp(
    val name: String,
    val executable: String,
    val icon: String?,
    val desktopFile: String,
    val terminal: Boolean
)

internal object LinuxAppInventory {
    private const val ENTRY_SEPARATOR = "__PIXEL_DESK_ENTRY__"

    fun parseDesktopEntries(output: String): List<LinuxDesktopApp> {
        return output.split(ENTRY_SEPARATOR)
            .mapNotNull(::parseEntry)
            .distinctBy { it.desktopFile }
            .sortedBy { it.name.lowercase() }
    }

    private fun parseEntry(raw: String): LinuxDesktopApp? {
        val values = linkedMapOf<String, String>()
        raw.lineSequence().forEach { line ->
            val index = line.indexOf('=')
            if (index > 0) {
                val key = line.substring(0, index).trim()
                if (key in ALLOWED_KEYS) values[key] = line.substring(index + 1).trim()
            }
        }
        if (values["Type"]?.ifBlank { "Application" } != "Application") return null
        if (values["NoDisplay"].equals("true", ignoreCase = true)) return null
        if (values["Hidden"].equals("true", ignoreCase = true)) return null
        val name = values["Name"]?.takeIf { it.isNotBlank() } ?: return null
        val executable = values["Exec"]?.takeIf { it.isNotBlank() } ?: return null
        val desktopFile = values["PATH"]?.takeIf { it.startsWith("/") } ?: return null
        return LinuxDesktopApp(
            name = name,
            executable = executable,
            icon = values["Icon"]?.takeIf { it.isNotBlank() },
            desktopFile = desktopFile,
            terminal = values["Terminal"].equals("true", ignoreCase = true)
        )
    }

    private val ALLOWED_KEYS = setOf("Name", "Exec", "Icon", "Type", "NoDisplay", "Hidden", "Terminal", "PATH")
}
