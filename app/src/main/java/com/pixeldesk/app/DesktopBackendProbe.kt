package com.pixeldesk.app

import android.content.Context

internal data class DesktopBackendStatus(
    val termuxVersion: String?,
    val x11Version: String?
) {
    val termuxInstalled: Boolean get() = termuxVersion != null
    val x11Installed: Boolean get() = x11Version != null
    val bothAndroidPackagesInstalled: Boolean get() = termuxInstalled && x11Installed
    val desktopSessionLaunchSupported: Boolean get() = false
}

internal object DesktopBackendProbe {
    const val TERMUX_PACKAGE = "com.termux"
    const val X11_PACKAGE = "com.termux.x11"

    fun read(context: Context): DesktopBackendStatus {
        val packageManager = context.packageManager
        return fromVersions(
            installedVersion(packageManager, TERMUX_PACKAGE),
            installedVersion(packageManager, X11_PACKAGE)
        )
    }

    internal fun fromVersions(termuxVersion: String?, x11Version: String?) =
        DesktopBackendStatus(termuxVersion, x11Version)

    private fun installedVersion(
        packageManager: android.content.pm.PackageManager,
        packageName: String
    ): String? = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
    }.getOrNull()
}
