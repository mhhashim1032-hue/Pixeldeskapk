package com.pixeldesk.app

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Common lifecycle contract for pipe-backed and PTY-backed guest sessions. */
interface EnvironmentRuntimeSession : AutoCloseable {
    val isAlive: Boolean
}

/** Owns one PRoot shell process per environment; screens borrow sessions. */
object EnvironmentRuntimeManager {
    private val sessions = ConcurrentHashMap<String, EnvironmentRuntimeSession>()
    private val mutex = Mutex()
    @Volatile private var shuttingDown = false
    @Volatile private var selectedId: String? = null

    fun resume() { shuttingDown = false }

    suspend fun start(context: Context, workspace: EnvironmentWorkspace): ProotInteractiveSession =
        mutex.withLock {
            check(!shuttingDown) { "PIXEL DESK is shutting down; retry after it resumes." }
            val existing = sessions[workspace.id]
            if (existing is ProotInteractiveSession && existing.isAlive) {
                selectedId = workspace.id
                return@withLock existing
            }
            existing?.close()
            sessions.remove(workspace.id)
            ProotInteractiveSession.start(context, workspace).also { session ->
                if (shuttingDown) {
                    session.close()
                    error("PIXEL DESK shut down while the environment was starting.")
                }
                sessions[workspace.id] = session
                selectedId = workspace.id
            }
        }

    internal suspend fun startPty(
        context: Context,
        workspace: EnvironmentWorkspace,
        rows: Int = 32,
        columns: Int = 100
    ): ProotPtySession = mutex.withLock {
        check(!shuttingDown) { "PIXEL DESK is shutting down; retry after it resumes." }
        val existing = sessions[workspace.id]
        if (existing is ProotPtySession && existing.isAlive) {
            selectedId = workspace.id
            return@withLock existing
        }
        existing?.close()
        sessions.remove(workspace.id)
        ProotPtySession.start(context, workspace, rows, columns).also { session ->
            if (shuttingDown) {
                session.close()
                error("PIXEL DESK shut down while the environment was starting.")
            }
            sessions[workspace.id] = session
            selectedId = workspace.id
        }
    }

    /**
     * Runs a blocking snapshot/export operation while holding the same lock
     * used by start/stop. A new shell cannot start halfway through a backup.
     */
    suspend fun <T> withEnvironmentStopped(environmentId: String, operation: () -> T): T =
        mutex.withLock {
            check(!shuttingDown) { "PIXEL DESK is shutting down; retry after it resumes." }
            check(sessions[environmentId]?.isAlive != true) {
                "Stop the environment before creating a backup."
            }
            operation()
        }

    suspend fun stop(environmentId: String): Boolean = mutex.withLock {
        val session = sessions.remove(environmentId)
        if (selectedId == environmentId) selectedId = null
        session?.close()
        session != null
    }

    fun select(environmentId: String) { selectedId = environmentId }
    fun selectedEnvironmentId(): String? = selectedId
    fun isRunning(environmentId: String): Boolean = sessions[environmentId]?.isAlive == true

    fun stopAll() {
        shuttingDown = true
        sessions.values.forEach { it.close() }
        sessions.clear()
        selectedId = null
    }
}
