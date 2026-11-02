package com.pixeldesk.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopBackendProbeTest {
    @Test
    fun reportsEachExternalPackageIndependently() {
        val termuxOnly = DesktopBackendProbe.fromVersions("0.119.0", null)
        assertTrue(termuxOnly.termuxInstalled)
        assertFalse(termuxOnly.x11Installed)
        assertFalse(termuxOnly.bothAndroidPackagesInstalled)
        val x11Only = DesktopBackendProbe.fromVersions(null, "nightly")
        assertFalse(x11Only.termuxInstalled)
        assertTrue(x11Only.x11Installed)
        assertFalse(x11Only.bothAndroidPackagesInstalled)
    }

    @Test
    fun packagePresenceDoesNotClaimDesktopLaunchIsSupported() {
        val status = DesktopBackendProbe.fromVersions("0.119.0", "nightly")
        assertTrue(status.bothAndroidPackagesInstalled)
        assertFalse(status.desktopSessionLaunchSupported)
    }
}
