package com.pixeldesk.app

import android.content.Context
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class ProotRuntimeFiles(
    val executable: File,
    val loader: File,
    val loader32: File?
)

data class ProotRuntimeResult(
    val success: Boolean,
    val message: String,
    val exitCode: Int? = null
)

object ProotRuntimeProbe {
    private const val MAX_OUTPUT_BYTES = 16 * 1024

    fun locate(context: Context): ProotRuntimeFiles? =
        locate(File(context.applicationInfo.nativeLibraryDir), Build.SUPPORTED_ABIS)

    internal fun locate(nativeLibraryDir: File, supportedAbis: Array<String>): ProotRuntimeFiles? {
        if (!supportedAbis.contains("arm64-v8a")) return null

        val executable = File(nativeLibraryDir, "libproot.so")
        val loader = File(nativeLibraryDir, "libproot_loader.so")
        val loader32 = File(nativeLibraryDir, "libproot_loader32.so")

        if (!executable.isFile || !executable.canRead() || !executable.canExecute()) return null
        if (!loader.isFile || !loader.canRead()) return null

        return ProotRuntimeFiles(
            executable = executable,
            loader = loader,
            loader32 = loader32.takeIf { it.isFile && it.canRead() }
        )
    }

    suspend fun check(context: Context, timeoutMs: Long = 10_000): ProotRuntimeResult {
        if (!Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
            return ProotRuntimeResult(false, "This build requires an ARM64 Android device.")
        }
        val files = locate(context)
            ?: return ProotRuntimeResult(
                false,
                "The native PRoot payload is missing or not executable in Android nativeLibraryDir."
            )
        return execute(files, listOf("--version"), timeoutMs)
    }

    internal suspend fun execute(
        files: ProotRuntimeFiles,
        arguments: List<String>,
        timeoutMs: Long
    ): ProotRuntimeResult = withContext(Dispatchers.IO) {
        require(timeoutMs > 0) { "Timeout must be positive." }
        require(arguments.isNotEmpty()) { "At least one PRoot argument is required." }

        val process = try {
            ProcessBuilder(listOf(files.executable.absolutePath) + arguments).apply {
                redirectErrorStream(true)
                environment()["PROOT_LOADER"] = files.loader.absolutePath
                files.loader32?.let { environment()["PROOT_LOADER_32"] = it.absolutePath }
            }.start()
        } catch (error: Exception) {
            return@withContext ProotRuntimeResult(
                false,
                "Could not start the native PRoot executable: ${error.message ?: error.javaClass.simpleName}"
            )
        }

        try {
            coroutineScope {
                val outputTask = async(Dispatchers.IO) {
                    process.inputStream.use { it.readLimitedUtf8(MAX_OUTPUT_BYTES) }
                }
                val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    outputTask.cancel()
                    return@coroutineScope ProotRuntimeResult(
                        false,
                        "PRoot did not respond within ${timeoutMs} ms."
                    )
                }

                val output = outputTask.await().trim().ifBlank { "(PRoot returned no output.)" }
                val exitCode = process.exitValue()
                ProotRuntimeResult(
                    success = exitCode == 0,
                    message = output,
                    exitCode = exitCode
                )
            }
        } catch (error: Exception) {
            process.destroyForcibly()
            ProotRuntimeResult(false, "PRoot probe failed: ${error.message ?: error.javaClass.simpleName}")
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun InputStream.readLimitedUtf8(maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var total = 0
        var truncated = false
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            val keep = (maxBytes - total).coerceAtLeast(0).coerceAtMost(count)
            if (keep > 0) {
                output.write(buffer, 0, keep)
                total += keep
            }
            if (keep < count) truncated = true
        }
        return output.toString(Charsets.UTF_8.name()) + if (truncated) "\n…output truncated…" else ""
    }
}
