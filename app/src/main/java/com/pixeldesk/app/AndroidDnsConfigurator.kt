package com.pixeldesk.app

import android.content.Context
import android.net.ConnectivityManager
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

data class DnsConfigurationResult(
    val configured: Boolean,
    val nameservers: List<String>,
    val message: String
)

object AndroidDnsConfigurator {
    internal fun renderResolvConf(addresses: List<String>): String {
        require(addresses.isNotEmpty()) { "At least one DNS server is required." }
        addresses.forEach { address ->
            val ipv4 = address.matches(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) &&
                address.split('.').all { it.toIntOrNull() in 0..255 }
            val ipv6 = address.contains(':') && address.matches(Regex("[0-9a-fA-F:]+"))
            require(ipv4 || ipv6) { "Invalid DNS server address." }
        }
        return addresses.distinct().joinToString(separator = "\\n", postfix = "\\n") {
            "nameserver $it"
        } + "options timeout:2 attempts:2\\n"
    }

    fun configure(context: Context, rootfs: File): DnsConfigurationResult {
        val root = rootfs.canonicalFile.toPath()
        require(rootfs.isDirectory && !Files.isSymbolicLink(rootfs.toPath())) {
            "Debian root filesystem is missing or unsafe."
        }
        val etc = File(rootfs, "etc")
        require(etc.isDirectory && !Files.isSymbolicLink(etc.toPath()) &&
            etc.canonicalFile.toPath().startsWith(root)
        ) { "Debian /etc directory is missing or unsafe." }

        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return DnsConfigurationResult(false, emptyList(), "Android network service is unavailable.")
        val network = connectivity.activeNetwork
            ?: return DnsConfigurationResult(false, emptyList(), "No active Android network connection.")
        val addresses = connectivity.getLinkProperties(network)?.dnsServers.orEmpty()
            .filter { address ->
                address is Inet4Address || (address is Inet6Address && address.scopeId == 0)
            }
            .map { it.hostAddress.substringBefore('%') }
            .distinct()
        if (addresses.isEmpty()) {
            return DnsConfigurationResult(false, emptyList(), "Android did not provide usable DNS servers.")
        }

        val destination = File(etc, "resolv.conf")
        require(!destination.exists() || destination.isFile || Files.isSymbolicLink(destination.toPath())) {
            "Debian /etc/resolv.conf is not a regular file."
        }
        val content = renderResolvConf(addresses)
        val temporary = Files.createTempFile(etc.toPath(), ".pixeldesk-resolv-", ".tmp")
        try {
            temporary.toFile().writeText(content)
            try {
                Files.move(temporary, destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, destination.toPath(), REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        return DnsConfigurationResult(true, addresses, "Configured Debian DNS from the active Android network.")
    }
}
