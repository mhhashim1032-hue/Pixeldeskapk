package com.pixeldesk.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class EnvironmentRuntimeManagerTest {
    @Test fun backupLeaseRunsOnlyWhileTheRuntimeIsStopped() = runBlocking {
        EnvironmentRuntimeManager.resume()
        var executed = false
        val result = EnvironmentRuntimeManager.withEnvironmentStopped("debian-arm64") {
            executed = true
            "backup-complete"
        }
        assertTrue(executed)
        assertEquals("backup-complete", result)
    }

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
