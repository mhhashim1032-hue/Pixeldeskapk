package com.pixeldesk.app

import android.content.Context
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

data class InteractiveCommandResult(
    val command: String,
    val exitCode: Int?,
    val output: String,
    val durationMs: Long,
    val timedOut: Boolean = false,
    val sessionAlive: Boolean = true
) {
    val success: Boolean get() = exitCode == 0 && !timedOut
}

/** Persistent sequential shell using pipes, not a PTY. Full-screen apps and job control are unsupported. */
class ProotInteractiveSession private constructor(
    private val process: Process,
    private val writer: OutputStreamWriter,
    private val output: Channel<String>,
    private val scope: CoroutineScope
) : AutoCloseable {
    private val commandMutex = Mutex()
    @Volatile private var closed = false

    suspend fun execute(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): InteractiveCommandResult =
        commandMutex.withLock {
            require(!closed && process.isAlive) { "Linux terminal session is not running." }
            require(command.isNotBlank()) { "Enter a Linux command." }
            require(command.length <= MAX_COMMAND_CHARS && command.indexOf(0.toChar()) < 0) {
                "Command is too long or contains an invalid character."
            }
            require(timeoutMs in 1_000L..300_000L) { "Timeout must be between 1 and 300 seconds." }

            val started = System.nanoTime()
            val token = "__PIXEL_DESK_${UUID.randomUUID().toString().replace("-", "")}__"
            val marker = "\n$token:"
            try {
                writer.write(command)
                writer.write("\nprintf '\\n%s:%s\\n' '$token' \"\$?\"\n")
                writer.flush()
            } catch (error: Exception) {
                close()
                return@withLock InteractiveCommandResult(
                    command, null, "Could not write to Linux shell: ${error.message ?: "I/O error"}",
                    elapsedMs(started), sessionAlive = false
                )
            }

            val captured = StringBuilder()
            val pending = StringBuilder()
            var exitCode: Int? = null
            val completed = withTimeoutOrNull(timeoutMs) {
                while (true) {
                    val chunk = output.receiveCatching().getOrNull() ?: break
                    pending.append(chunk)
                    val markerIndex = pending.indexOf(marker)
                    if (markerIndex >= 0) {
                        if (markerIndex > 0) {
                            appendBounded(captured, pending.substring(0, markerIndex))
                            pending.delete(0, markerIndex)
                        }
                        val statusStart = marker.length
                        val statusEnd = pending.indexOf("\n", statusStart)
                        if (statusEnd >= 0) {
                            exitCode = pending.substring(statusStart, statusEnd).trim().toIntOrNull()
                            return@withTimeoutOrNull true
                        }
                    } else if (pending.length > marker.length + 32) {
                        val flushCount = pending.length - (marker.length + 32)
                        appendBounded(captured, pending.substring(0, flushCount))
                        pending.delete(0, flushCount)
                    }
                }
                false
            } ?: false

            if (!completed) {
                close()
                return@withLock InteractiveCommandResult(
                    command, null,
                    captured.toString() + pending.toString().take((MAX_OUTPUT_CHARS - captured.length).coerceAtLeast(0)),
                    elapsedMs(started), timedOut = true, sessionAlive = false
                )
            }

            InteractiveCommandResult(command, exitCode, captured.toString(), elapsedMs(started), sessionAlive = process.isAlive)
        }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { writer.close() }
        if (process.isAlive) process.destroyForcibly()
        output.close()
        scope.cancel()
    }

    private fun appendBounded(target: StringBuilder, value: String) {
        val remaining = (MAX_OUTPUT_CHARS - target.length).coerceAtLeast(0)
        if (remaining > 0) target.append(value.take(remaining))
        if (value.length > remaining && !target.endsWith("\n…output truncated…")) {
            val suffix = "\n…output truncated…"
            if (target.length + suffix.length <= MAX_OUTPUT_CHARS) target.append(suffix)
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val MAX_COMMAND_CHARS = 8_192
        private const val MAX_OUTPUT_CHARS = 64 * 1024

        suspend fun start(context: Context, workspace: EnvironmentWorkspace): ProotInteractiveSession =
            withContext(Dispatchers.IO) {
                val files = ProotRuntimeProbe.locate(context)
                    ?: error("The native ARM64 PRoot runtime is unavailable.")
                start(files, workspace)
            }

        internal fun start(files: ProotRuntimeFiles, workspace: EnvironmentWorkspace): ProotInteractiveSession {
            val workspaceRoot = workspace.directory.canonicalFile.toPath()
            require(workspace.rootFilesystem.canonicalFile.toPath().parent == workspaceRoot &&
                workspace.rootFilesystem.isDirectory
            ) { "The Debian root filesystem is not installed in this workspace." }
            listOf(workspace.home, workspace.downloads, workspace.temporary).forEach { directory ->
                require(directory.canonicalFile.toPath().parent == workspaceRoot &&
                    directory.isDirectory && !java.nio.file.Files.isSymbolicLink(directory.toPath())
                ) { "Workspace directory ${directory.name} is missing or unsafe." }
            }
            val validation = RootfsValidator.validate(workspace.rootFilesystem)
            require(files.executable.isFile && files.executable.canExecute() &&
                files.loader.isFile && files.loader.canRead()
            ) { "The native PRoot executable or loader is missing." }

            val args = listOf(
                "-0", "-L", "-r", workspace.rootFilesystem.canonicalPath,
                "-w", "/root", "-b", "/dev", "-b", "/proc", "-b", "/sys",
                "-b", "${workspace.home.canonicalPath}:/root",
                "-b", "${workspace.downloads.canonicalPath}:/root/Downloads",
                "-b", "${workspace.temporary.canonicalPath}:/tmp",
                validation.shellPath, "-i"
            )
            val process = ProcessBuilder(listOf(files.executable.absolutePath) + args).apply {
                redirectErrorStream(true)
                environment()["PROOT_LOADER"] = files.loader.absolutePath
                files.loader32?.let { environment()["PROOT_LOADER_32"] = it.absolutePath }
                environment()["PROOT_TMP_DIR"] = workspace.temporary.canonicalPath
                environment()["HOME"] = "/root"
                environment()["TMPDIR"] = "/tmp"
                environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                environment()["LANG"] = "C.UTF-8"
                environment()["TERM"] = "dumb"
                environment()["PS1"] = ""
                environment()["PS2"] = ""
                environment()["ENV"] = "/dev/null"
            }.start()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val channel = Channel<String>(capacity = 16)
            scope.launch {
                try {
                    val reader = InputStreamReader(process.inputStream, Charsets.UTF_8)
                    val buffer = CharArray(2_048)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        channel.send(String(buffer, 0, count))
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Process termination closes the stream and ends the reader.
                } finally {
                    channel.close()
                }
            }
            return ProotInteractiveSession(
                process, OutputStreamWriter(process.outputStream, Charsets.UTF_8), channel, scope
            )
        }

        private fun elapsedMs(started: Long): Long =
            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
    }
}
