package com.pixeldesk.app

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns one PRoot shell process per environment; screens borrow sessions. */
object EnvironmentRuntimeManager {
    private val sessions = ConcurrentHashMap<String, ProotInteractiveSession>()
    private val mutex = Mutex()
    @Volatile private var shuttingDown = false
    @Volatile private var selectedId: String? = null

    fun resume() { shuttingDown = false }

    suspend fun start(context: Context, workspace: EnvironmentWorkspace): ProotInteractiveSession =
        mutex.withLock {
            check(!shuttingDown) { "PIXEL DESK is shutting down; retry after it resumes." }
            val existing = sessions[workspace.id]
            if (existing?.isAlive == true) {
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
