package com.pixeldesk.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EnvironmentRuntimeManagerTest {
    @Test fun selectionIsExplicitAndStopAllClearsSelection() {
        EnvironmentRuntimeManager.resume()
        EnvironmentRuntimeManager.select("debian-arm64")
        assertEquals("debian-arm64", EnvironmentRuntimeManager.selectedEnvironmentId())
        EnvironmentRuntimeManager.stopAll()
        assertEquals(null, EnvironmentRuntimeManager.selectedEnvironmentId())
        assertFalse(EnvironmentRuntimeManager.isRunning("debian-arm64"))
        EnvironmentRuntimeManager.resume()
    }
}
