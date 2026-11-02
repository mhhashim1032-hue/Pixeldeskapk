package com.pixeldesk.app

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotRuntimeProbeTest {
    @Test
    fun locatesPayloadOnlyOnArm64WithExecutableAndLoader() {
        val dir = Files.createTempDirectory("proot-locate").toFile()
        try {
            val executable = File(dir, "libproot.so").apply { writeText("binary"); setExecutable(true) }
            File(dir, "libproot_loader.so").writeText("loader")

            val files = ProotRuntimeProbe.locate(dir, arrayOf("arm64-v8a", "armeabi-v7a"))

            assertNotNull(files)
            assertEquals(executable, files?.executable)
            assertNull(ProotRuntimeProbe.locate(dir, arrayOf("x86_64")))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun doesNotLocatePayloadWhenLoaderIsMissing() {
        val dir = Files.createTempDirectory("proot-no-loader").toFile()
        try {
            File(dir, "libproot.so").apply { writeText("binary"); setExecutable(true) }
            assertNull(ProotRuntimeProbe.locate(dir, arrayOf("arm64-v8a")))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun capturesSuccessfulCommandOutputAndExitCode() = runBlocking {
        val files = fakeRuntime("printf 'PRoot mock version\\n'; exit 0")
        try {
            val result = ProotRuntimeProbe.execute(files, listOf("--version"), 2_000)
            assertTrue(result.success)
            assertEquals(0, result.exitCode)
            assertTrue(result.message.contains("PRoot mock version"))
        } finally {
            files.executable.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun terminatesCommandsThatExceedTimeout() = runBlocking {
        val files = fakeRuntime("sleep 5; echo late")
        try {
            val result = ProotRuntimeProbe.execute(files, listOf("--version"), 100)
            assertFalse(result.success)
            assertTrue(result.message.contains("did not respond"))
        } finally {
            files.executable.parentFile?.deleteRecursively()
        }
    }

    private fun fakeRuntime(body: String): ProotRuntimeFiles {
        val dir = Files.createTempDirectory("proot-fake").toFile()
        val executable = File(dir, "fake-proot").apply {
            writeText("#!/system/bin/sh\n$body\n")
            setExecutable(true)
        }
        val loader = File(dir, "fake-loader").apply { writeText("loader") }
        return ProotRuntimeFiles(executable, loader, null)
    }
}
