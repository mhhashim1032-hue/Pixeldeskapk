package com.pixeldesk.app

/**
 * Thin JNI boundary for Android's POSIX pseudo-terminal API.
 *
 * The native implementation owns the PTY master descriptor and child process.
 * Handles are opaque to Kotlin and remain valid until close() is called.
 */
internal object NativePtyBridge {
    init {
        System.loadLibrary("pixeldeskpty")
    }

    @JvmStatic external fun spawn(
        executable: String,
        arguments: Array<String>,
        environment: Array<String>,
        rows: Int,
        columns: Int
    ): Long

    @JvmStatic external fun read(handle: Long, destination: ByteArray, offset: Int, length: Int): Int
    @JvmStatic external fun write(handle: Long, source: ByteArray, offset: Int, length: Int): Int
    @JvmStatic external fun resize(handle: Long, rows: Int, columns: Int): Boolean
    @JvmStatic external fun sendSignal(handle: Long, signal: Int): Boolean
    @JvmStatic external fun close(handle: Long): Boolean
}
