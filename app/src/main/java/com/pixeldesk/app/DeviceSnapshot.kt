package com.pixeldesk.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs

data class DeviceSnapshot(
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val totalStorageBytes: Long,
    val availableStorageBytes: Long,
    val supportedAbis: List<String>
) {
    val supportsArm64: Boolean get() = supportedAbis.any { it == "arm64-v8a" }
    val primaryAbi: String get() = supportedAbis.firstOrNull() ?: "Unknown"
    companion object {
        fun read(context: Context): DeviceSnapshot {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
            val statFs = StatFs(Environment.getDataDirectory().absolutePath)
            return DeviceSnapshot(
                model = Build.MODEL.ifBlank { "Unknown device" },
                androidVersion = Build.VERSION.RELEASE ?: "Unknown",
                apiLevel = Build.VERSION.SDK_INT,
                totalRamBytes = memoryInfo.totalMem,
                availableRamBytes = memoryInfo.availMem,
                totalStorageBytes = statFs.totalBytes,
                availableStorageBytes = statFs.availableBytes,
                supportedAbis = Build.SUPPORTED_ABIS.toList()
            )
        }
    }
}

fun Long.toReadableSize(): String {
    if (this < 0L) return "Unavailable"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "${value.toLong()} ${units[unit]}" else String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
}
