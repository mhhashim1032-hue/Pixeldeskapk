package com.pixeldesk.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSnapshotCompatibilityTest {
    @Test
    fun detectsArm64AbiRegardlessOfSecondaryAbis() {
        val snapshot = snapshot(listOf("armeabi-v7a", "arm64-v8a"))
        assertTrue(snapshot.supportsArm64)
        assertEqualsPrimary("armeabi-v7a", snapshot.primaryAbi)
    }

    @Test
    fun reportsUnsupportedWhenArm64IsAbsent() {
        val snapshot = snapshot(listOf("x86_64"))
        assertFalse(snapshot.supportsArm64)
    }

    private fun snapshot(abis: List<String>) = DeviceSnapshot(
        model = "Test device",
        androidVersion = "15",
        apiLevel = 35,
        totalRamBytes = 8L,
        availableRamBytes = 4L,
        totalStorageBytes = 128L,
        availableStorageBytes = 64L,
        supportedAbis = abis
    )

    private fun assertEqualsPrimary(expected: String, actual: String) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
