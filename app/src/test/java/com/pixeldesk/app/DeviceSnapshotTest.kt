package com.pixeldesk.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSnapshotTest {
    @Test
    fun readableSizeFormatsBytesAndGigabytes() {
        assertEquals("512 B", 512L.toReadableSize())
        assertEquals("1.0 GB", (1024L * 1024L * 1024L).toReadableSize())
    }

    @Test
    fun readableSizeHandlesNegativeAsUnavailable() {
        assertEquals("Unavailable", (-1L).toReadableSize())
    }
}
