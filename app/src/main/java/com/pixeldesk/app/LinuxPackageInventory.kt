package com.pixeldesk.app

internal data class InstalledLinuxPackage(
    val name: String,
    val version: String
)

internal object LinuxPackageInventory {
    private val packageNamePattern = Regex("[a-z0-9][a-z0-9+.-]{0,127}(?::[a-z0-9][a-z0-9-]{0,31})?")

    fun parse(output: String): List<InstalledLinuxPackage> =
        output.lineSequence()
            .mapNotNull { line ->
                val fields = line.split('\t')
                if (fields.size < 3) return@mapNotNull null
                val name = fields[0].trim()
                val version = fields[1].trim()
                val status = fields[2].trim()
                if (!packageNamePattern.matches(name) || version.isBlank() || status != "installed") {
                    return@mapNotNull null
                }
                InstalledLinuxPackage(name, version)
            }
            .distinctBy { it.name }
            .sortedBy { it.name.lowercase() }
}
