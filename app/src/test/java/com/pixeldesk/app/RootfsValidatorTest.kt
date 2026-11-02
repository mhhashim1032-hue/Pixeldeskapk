package com.pixeldesk.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class RootfsValidatorTest {
    @Test
    fun validatesPinnedDebianArm64Rootfs() {
        val rootfs = createRootfs(EM_AARCH64)
        try {
            val result = RootfsValidator.validate(rootfs)
            assertEquals("debian", result.distribution)
            assertEquals("12", result.release)
            assertEquals("arm64", result.architecture)
            assertEquals("/usr/bin/dash", result.shellPath)
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun rejectsNonArm64Shell() {
        val rootfs = createRootfs(EM_X86_64)
        try {
            runCatching { RootfsValidator.validate(rootfs) }
                .onSuccess { error("x86_64 rootfs should not pass ARM64 validation.") }
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun rejectsNonDebianRootfs() {
        val rootfs = createRootfs(EM_AARCH64, osRelease = "ID=ubuntu\nVERSION_ID=\"24.04\"\n")
        try {
            runCatching { RootfsValidator.validate(rootfs) }
                .onSuccess { error("Ubuntu should not pass the Debian catalog validator.") }
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun catalogUsesPinnedHttpsSha256Artifact() {
        val artifact = RootfsCatalog.debianBookwormArm64
        assertTrue(artifact.url.startsWith("https://"))
        assertEquals(64, artifact.sha256.length)
        assertEquals("arm64", artifact.architecture)
        assertEquals("12", artifact.release)
    }

    private fun createRootfs(machine: Int, osRelease: String = "ID=debian\nVERSION_ID=\"12\"\n"): File {
        val rootfs = Files.createTempDirectory("pixeldesk-rootfs-validator").toFile()
        File(rootfs, "etc").mkdirs()
        File(rootfs, "usr/bin").mkdirs()
        File(rootfs, "etc/os-release").writeText(osRelease)
        File(rootfs, "usr/bin/dpkg").writeText("dpkg placeholder")

        val header = ByteArray(20)
        header[0] = 0x7f
        header[1] = 'E'.code.toByte()
        header[2] = 'L'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = 2
        header[5] = 1
        header[18] = (machine and 0xff).toByte()
        header[19] = ((machine shr 8) and 0xff).toByte()
        File(rootfs, "usr/bin/dash").writeBytes(header)
        return rootfs
    }

    companion object {
        private const val EM_AARCH64 = 183
        private const val EM_X86_64 = 62
    }
}
