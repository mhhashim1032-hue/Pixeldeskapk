package com.pixeldesk.app

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class EnvironmentState { STORAGE_ONLY, INSTALLING, INSTALLED, STARTING, RUNNING, STOPPING, INVALID }

/** Serializes install/start/stop/delete so destructive operations cannot race. */
class EnvironmentLifecycleController(
    private val context: Context,
    private val runtime: EnvironmentRuntimeManager = EnvironmentRuntimeManager
) {
    private val mutex = Mutex()
    private val installing = mutableSetOf<String>()

    fun state(workspace: EnvironmentWorkspace): EnvironmentState = when {
        workspace.id in installing -> EnvironmentState.INSTALLING
        runtime.isRunning(workspace.id) -> EnvironmentState.RUNNING
        !workspace.directory.exists() -> EnvironmentState.INVALID
        !workspace.rootFilesystem.exists() -> EnvironmentState.STORAGE_ONLY
        runCatching { RootfsValidator.validate(workspace.rootFilesystem) }.isSuccess -> EnvironmentState.INSTALLED
        else -> EnvironmentState.INVALID
    }

    suspend fun install(workspace: EnvironmentWorkspace): RootfsInstallResult = mutex.withLock {
        check(workspace.directory.exists()) { "Environment workspace no longer exists." }
        check(workspace.id !in installing) { "An installation is already in progress." }
        check(!runtime.isRunning(workspace.id)) { "Stop the environment before installing Debian." }
        installing += workspace.id
        try { RootfsInstaller.install(workspace) } finally { installing -= workspace.id }
    }

    suspend fun start(workspace: EnvironmentWorkspace): ProotInteractiveSession = mutex.withLock {
        check(workspace.directory.exists()) { "Environment workspace no longer exists." }
        check(workspace.id !in installing) { "Wait for Debian installation to finish." }
        RootfsValidator.validate(workspace.rootFilesystem)
        runtime.start(context, workspace)
    }

    suspend fun stop(workspace: EnvironmentWorkspace): Boolean = mutex.withLock {
        runtime.stop(workspace.id)
    }

    suspend fun delete(workspace: EnvironmentWorkspace): Boolean = mutex.withLock {
        check(workspace.id !in installing) { "Cannot delete an environment while installation is active." }
        runtime.stop(workspace.id)
        WorkspaceStorage.delete(context, workspace)
    }
}
