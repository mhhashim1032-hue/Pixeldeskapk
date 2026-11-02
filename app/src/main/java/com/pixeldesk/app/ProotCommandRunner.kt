package com.pixeldesk.app

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class LinuxCommandResult(
    val command: String,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val timedOut: Boolean = false
) {
    val success: Boolean get() = exitCode == 0 && !timedOut
}

object ProotCommandRunner {
    private const val DEFAULT_TIMEOUT_MS = 30_000L
    private const val MAX_OUTPUT_BYTES = 64 * 1024
    private const val POLL_INTERVAL_MS = 100L

    suspend fun run(
        context: Context,
        workspace: EnvironmentWorkspace,
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): LinuxCommandResult {
        val files = ProotRuntimeProbe.locate(context)
            ?: error("The native ARM64 PRoot runtime is unavailable.")
        return run(files, workspace, command, timeoutMs)
    }

    internal suspend fun run(
        files: ProotRuntimeFiles,
        workspace: EnvironmentWorkspace,
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): LinuxCommandResult = withContext(Dispatchers.IO) {
        require(command.isNotBlank()) { "Enter a Linux command." }
        require(command.length <= 8_192 && command.indexOf(0.toChar()) < 0) {
            "Command is too long or contains an invalid character."
        }
        require(timeoutMs in 1_000L..300_000L) {
            "Command timeout must be between 1 and 300 seconds."
        }

        val root = workspace.directory.canonicalFile.toPath()
        require(workspace.rootFilesystem.canonicalFile.toPath().parent == root &&
            workspace.rootFilesystem.isDirectory
        ) { "The Debian root filesystem is not installed in this workspace." }
        listOf(workspace.home, workspace.downloads, workspace.temporary).forEach { directory ->
            require(directory.canonicalFile.toPath().parent == root &&
                directory.isDirectory && !java.nio.file.Files.isSymbolicLink(directory.toPath())
            ) { "Workspace directory ${directory.name} is missing or unsafe." }
        }
        val validation = RootfsValidator.validate(workspace.rootFilesystem)
        require(files.executable.isFile && files.executable.canExecute() &&
            files.loader.isFile && files.loader.canRead()
        ) { "The native PRoot executable or loader is missing." }

        val args = buildArguments(workspace, validation.shellPath, command)
        executeProcess(files, args, command, timeoutMs)
    }

    internal fun buildArguments(
        workspace: EnvironmentWorkspace,
        shellPath: String,
        command: String
    ): List<String> {
        val rootfs = workspace.rootFilesystem.canonicalPath
        val home = workspace.home.canonicalPath
        val downloads = workspace.downloads.canonicalPath
        val temporary = workspace.temporary.canonicalPath
        return listOf(
            "-0",
            "-L",
            "-r", rootfs,
            "-w", "/root",
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "$home:/root",
            "-b", "$downloads:/root/Downloads",
            "-b", "$temporary:/tmp",
            shellPath,
            "-c",
            command
        )
    }

    private suspend fun executeProcess(
        files: ProotRuntimeFiles,
        arguments: List<String>,
        command: String,
        timeoutMs: Long
    ): LinuxCommandResult = coroutineScope {
        val startedAt = System.nanoTime()
        val process = try {
            ProcessBuilder(listOf(files.executable.absolutePath) + arguments).apply {
                environment()["PROOT_LOADER"] = files.loader.absolutePath
                files.loader32?.let { environment()["PROOT_LOADER_32"] = it.absolutePath }
                environment()["HOME"] = "/root"
                environment()["TMPDIR"] = "/tmp"
                environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                environment()["LANG"] = "C.UTF-8"
                environment()["TERM"] = "xterm-256color"
            }.start()
        } catch (error: Exception) {
            return@coroutineScope LinuxCommandResult(
                command = command,
                exitCode = null,
                stdout = "",
                stderr = "Could not start PRoot: ${error.message ?: error.javaClass.simpleName}",
                durationMs = elapsedMs(startedAt)
            )
        }

        val stdoutTask = async(Dispatchers.IO) {
            process.inputStream.use { it.readLimitedUtf8(MAX_OUTPUT_BYTES) }
        }
        val stderrTask = async(Dispatchers.IO) {
            process.errorStream.use { it.readLimitedUtf8(MAX_OUTPUT_BYTES) }
        }

        try {
            val context = currentCoroutineContext()
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
            while (process.isAlive && System.nanoTime() < deadline) {
                context.ensureActive()
                process.waitFor(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
            }

            if (process.isAlive) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                stdoutTask.cancel()
                stderrTask.cancel()
                return@coroutineScope LinuxCommandResult(
                    command = command,
                    exitCode = null,
                    stdout = "",
                    stderr = "Command timed out after ${timeoutMs / 1_000} seconds.",
                    durationMs = elapsedMs(startedAt),
                    timedOut = true
                )
            }

            val stdout = stdoutTask.await()
            val stderr = stderrTask.await()
            LinuxCommandResult(
                command = command,
                exitCode = process.exitValue(),
                stdout = stdout,
                stderr = stderr,
                durationMs = elapsedMs(startedAt)
            )
        } catch (cancelled: CancellationException) {
            process.destroyForcibly()
            stdoutTask.cancel()
            stderrTask.cancel()
            throw cancelled
        } catch (error: Exception) {
            process.destroyForcibly()
            stdoutTask.cancel()
            stderrTask.cancel()
            LinuxCommandResult(
                command = command,
                exitCode = null,
                stdout = "",
                stderr = "Linux command failed: ${error.message ?: error.javaClass.simpleName}",
                durationMs = elapsedMs(startedAt)
            )
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun elapsedMs(startedAt: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

    private fun InputStream.readLimitedUtf8(maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(2_048)
        var kept = 0
        var truncated = false
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            val remaining = (maxBytes - kept).coerceAtLeast(0)
            val toKeep = count.coerceAtMost(remaining)
            if (toKeep > 0) {
                output.write(buffer, 0, toKeep)
                kept += toKeep
            }
            if (toKeep < count) truncated = true
        }
        return output.toString(Charsets.UTF_8.name()) +
            if (truncated) "\n…output truncated…" else ""
    }
}
