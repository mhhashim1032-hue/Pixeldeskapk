package com.pixeldesk.app

import org.junit.Assert.assertEquals
import org.junit.Test

class VerifiedDownloaderTest {
    @Test
    fun encodesSha256BytesAsLowercaseHex() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "abc".toByteArray().let {
                java.security.MessageDigest.getInstance("SHA-256").digest(it).toHexString()
            }
        )
    }

    @Test
    fun acceptsOnlyHttpsAndWellFormedDigests() {
        val digest = "a".repeat(64)
        VerifiedDownloadSpec("https://example.org/rootfs.tar.xz", digest, 1024L)

        try {
            VerifiedDownloadSpec("http://example.org/rootfs.tar.xz", digest, 1024L)
            throw AssertionError("HTTP should be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        try {
            VerifiedDownloadSpec("https://example.org/rootfs.tar.xz", "bad", 1024L)
            throw AssertionError("Malformed digest should be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
