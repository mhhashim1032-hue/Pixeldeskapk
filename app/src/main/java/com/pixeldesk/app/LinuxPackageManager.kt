package com.pixeldesk.app

data class PackageOperation(
    val title: String,
    val command: String,
    val timeoutMs: Long = 300_000L
)

object LinuxPackageManager {

data class LinuxQuickPackage(val name: String, val description: String, val category: String)

    val quickPackages = listOf(
        LinuxQuickPackage("nano", "Simple terminal text editor", "Editors"),
        LinuxQuickPackage("vim-tiny", "Lightweight modal text editor", "Editors"),
        LinuxQuickPackage("curl", "HTTP and API transfer utility", "Network"),
        LinuxQuickPackage("wget", "Download files from the command line", "Network"),
        LinuxQuickPackage("git", "Version control for source code", "Development"),
        LinuxQuickPackage("python3", "Python 3 interpreter", "Development"),
        LinuxQuickPackage("htop", "Interactive process monitor", "System"),
        LinuxQuickPackage("unzip", "Extract ZIP archives", "Utilities"),
        LinuxQuickPackage("zip", "Create ZIP archives", "Utilities"),
        LinuxQuickPackage("tree", "Display directory trees", "Utilities")
    )

    fun installedPackagesOperation() = PackageOperation(
        title = "List installed packages",
        command = "dpkg-query -W | head -n 120",
        timeoutMs = 60_000L
    )

    fun systemInfoOperation() = PackageOperation(
        title = "System information",
        command = "dpkg --print-architecture; uname -a; df -h /",
        timeoutMs = 30_000L
    )

    fun quickInstallOperation(packageName: String): PackageOperation {
        require(quickPackages.any { it.name == packageName }) { "Package is not in the curated software list." }
        return installOperation(packageName)
    }

    private val packageNamePattern = Regex("[a-z0-9][a-z0-9+.-]{0,127}")
    private const val MAX_PACKAGES_PER_OPERATION = 20

    fun parsePackageNames(input: String): List<String> {
        val names = input.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()
        require(names.isNotEmpty()) { "Enter at least one Debian package name." }
        require(names.size <= MAX_PACKAGES_PER_OPERATION) {
            "Install or remove no more than $MAX_PACKAGES_PER_OPERATION packages at once."
        }
        names.forEach { name ->
            require(packageNamePattern.matches(name)) {
                "Invalid Debian package name: $name"
            }
        }
        return names
    }

    fun updateOperation() = PackageOperation(
        title = "Refresh package indexes",
        command = "apt-get -o Acquire::Retries=2 -o Acquire::http::Timeout=20 -o Acquire::https::Timeout=20 update"
    )

    fun installOperation(input: String): PackageOperation {
        val names = parsePackageNames(input)
        return PackageOperation(
            title = "Install ${names.joinToString()}",
            command = "DEBIAN_FRONTEND=noninteractive apt-get -y --no-install-recommends install -- ${names.joinToString(" ")}"
        )
    }

    fun removeOperation(input: String): PackageOperation {
        val names = parsePackageNames(input)
        return PackageOperation(
            title = "Remove ${names.joinToString()}",
            command = "DEBIAN_FRONTEND=noninteractive apt-get -y remove -- ${names.joinToString(" ")}"
        )
    }

    fun cleanOperation() = PackageOperation(
        title = "Clean package cache",
        command = "apt-get clean",
        timeoutMs = 60_000L
    )

    suspend fun run(
        session: ProotInteractiveSession,
        operation: PackageOperation
    ): InteractiveCommandResult = session.execute(operation.command, operation.timeoutMs)
}
