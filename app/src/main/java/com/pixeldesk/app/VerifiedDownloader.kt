package com.pixeldesk.app

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class VerifiedDownloadSpec(
    val url: String,
    val sha256: String,
    val maxBytes: Long
) {
    init {
        require(url.startsWith("https://", ignoreCase = true)) {
            "Downloads must use HTTPS."
        }
        require(sha256.matches(Regex("[a-fA-F0-9]{64}"))) {
            "A valid SHA-256 digest is required."
        }
        require(maxBytes > 0) { "Maximum download size must be positive." }
    }
}

data class VerifiedDownloadResult(
    val file: File,
    val bytesWritten: Long,
    val sha256: String
)

/**
 * Downloads an archive into app-private storage, verifies its SHA-256 digest,
 * then atomically promotes it from a .part file. It does not extract archives
 * or execute downloaded code.
 */
object VerifiedDownloader {
    suspend fun download(
        spec: VerifiedDownloadSpec,
        destination: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> }
    ): VerifiedDownloadResult = withContext(Dispatchers.IO) {
        val parent = destination.canonicalFile.parentFile
            ?: error("Destination must have a parent directory.")
        check(parent.exists() || parent.mkdirs()) { "Cannot create download directory." }

        val finalFile = destination.canonicalFile
        val partialFile = File(parent, finalFile.name + ".part")
        check(!finalFile.exists()) { "Destination already exists." }
        partialFile.delete()

        var connection: HttpURLConnection? = null
        try {
            val url = URL(spec.url)
            require(url.protocol.equals("https", ignoreCase = true)) { "HTTPS is required." }
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "PIXEL-DESK/0.1")
            }
            connection.connect()
            require(connection.url.protocol.equals("https", ignoreCase = true)) {
                "Insecure redirect refused."
            }
            check(connection.responseCode in 200..299) {
                "Download server returned HTTP ${connection.responseCode}."
            }

            val declaredLength = connection.contentLengthLong.takeIf { it >= 0L }
            require(declaredLength == null || declaredLength <= spec.maxBytes) {
                "Archive exceeds the allowed download size."
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partialFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        written += count
                        require(written <= spec.maxBytes) {
                            "Archive exceeds the allowed download size."
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(written, declaredLength)
                    }
                    output.fd.sync()
                }
            }

            val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
            check(actualDigest.equals(spec.sha256, ignoreCase = true)) {
                "SHA-256 verification failed. The downloaded archive was discarded."
            }
            check(partialFile.renameTo(finalFile)) {
                "Could not finalize verified download."
            }
            VerifiedDownloadResult(finalFile, written, actualDigest)
        } catch (error: Throwable) {
            partialFile.delete()
            throw error
        } finally {
            connection?.disconnect()
        }
    }
}
