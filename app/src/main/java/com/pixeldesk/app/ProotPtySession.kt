package com.pixeldesk.app

import android.content.Context
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Real POSIX PTY-backed PRoot session.
 *
 * Unlike ProotInteractiveSession, this class exposes byte-oriented terminal
 * input/output, window sizing and process-group signals. A terminal renderer
 * is still responsible for interpreting ANSI/VT control sequences.
 */
internal class ProotPtySession private constructor(
    private val handle: Long,
    private val scope: CoroutineScope,
    private val output: Channel<ByteArray>
) : EnvironmentRuntimeSession {
    private val closed = AtomicBoolean(false)
    @Volatile private var processAlive = true

    override val isAlive: Boolean get() = !closed.get() && processAlive

    suspend fun readOutput(): ByteArray? = output.receiveCatching().getOrNull()

    fun write(bytes: ByteArray) {
        check(!closed.get()) { "Linux PTY session is closed." }
        var offset = 0
        while (offset < bytes.size) {
            val written = NativePtyBridge.write(handle, bytes, offset, bytes.size - offset)
            if (written <= 0) throw IOException("Could not write to the Linux PTY.")
            offset += written
        }
    }

    fun writeText(value: String) = write(value.toByteArray(Charsets.UTF_8))

    /**
     * Write the terminal's VINTR byte. With the PTY's ISIG line discipline this
     * delivers SIGINT to the current foreground process group, including jobs
     * that have changed the terminal foreground group.
     */
    fun interrupt(): Boolean {
        if (closed.get()) return false
        val controlC = byteArrayOf(3)
        return NativePtyBridge.write(handle, controlC, 0, controlC.size) == 1
    }

    fun resize(rows: Int, columns: Int): Boolean {
        require(rows in 2..300 && columns in 2..500) { "Terminal dimensions are outside supported bounds." }
        return !closed.get() && NativePtyBridge.resize(handle, rows, columns)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        NativePtyBridge.close(handle)
        output.close()
        scope.cancel()
    }

    companion object {
        private const val MAX_READ_BYTES = 16 * 1024

        suspend fun start(
            context: Context,
            workspace: EnvironmentWorkspace,
            rows: Int = 32,
            columns: Int = 100
        ): ProotPtySession = withContext(Dispatchers.IO) {
            AndroidDnsConfigurator.configure(context, workspace.rootFilesystem)
            val files = ProotRuntimeProbe.locate(context)
                ?: error("The native ARM64 PRoot runtime is unavailable.")
            start(files, workspace, rows, columns)
        }

        internal fun start(
            files: ProotRuntimeFiles,
            workspace: EnvironmentWorkspace,
            rows: Int = 32,
            columns: Int = 100
        ): ProotPtySession {
            require(rows in 2..300 && columns in 2..500) { "Terminal dimensions are outside supported bounds." }
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

            val args = buildProotPtyArguments(workspace, validation.shellPath)
            val environment = buildProotPtyEnvironment(files, workspace)
            val handle = NativePtyBridge.spawn(
                files.executable.absolutePath, args, environment, rows, columns
            )
            check(handle != 0L) { "Could not create the native Linux pseudo-terminal." }

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val channel = Channel<ByteArray>(capacity = 8)
            val session = ProotPtySession(handle, scope, channel)
            scope.launch {
                val buffer = ByteArray(MAX_READ_BYTES)
                try {
                    while (true) {
                        val count = NativePtyBridge.read(handle, buffer, 0, buffer.size)
                        if (count <= 0) break
                        channel.send(buffer.copyOf(count))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // PTY closure is reported by the channel completing.
                } finally {
                    session.processAlive = false
                    channel.close()
                }
            }
            return session
        }
    }
}


internal fun buildProotPtyArguments(
    workspace: EnvironmentWorkspace,
    shellPath: String
): Array<String> = arrayOf(
    "-0", "-L", "-r", workspace.rootFilesystem.canonicalPath,
    "-w", "/root", "-b", "/dev", "-b", "/proc", "-b", "/sys",
    "-b", "${workspace.home.canonicalPath}:/root",
    "-b", "${workspace.downloads.canonicalPath}:/root/Downloads",
    "-b", "${workspace.temporary.canonicalPath}:/tmp",
    shellPath, "-i"
)

internal fun buildProotPtyEnvironment(
    files: ProotRuntimeFiles,
    workspace: EnvironmentWorkspace
): Array<String> = arrayOf(
    "PROOT_LOADER=${files.loader.absolutePath}",
    "PROOT_TMP_DIR=${workspace.temporary.canonicalPath}",
    "HOME=/root",
    "TMPDIR=/tmp",
    "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
    "LANG=C.UTF-8",
    "TERM=xterm-256color",
    "COLORTERM=truecolor",
    "PS1=\\u@pixeldesk:\\w\\$ ",
    "PS2=> "
)
