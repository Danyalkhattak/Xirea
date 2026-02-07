package com.dannyk.xirea.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Handles downloading of AI model files with progress tracking.
 */
class ModelDownloader(private val context: Context) {
    
    companion object {
        private const val TAG = "ModelDownloader"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 30_000
        private const val READ_TIMEOUT = 60_000
    }
    
    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progress: Int, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
        data class Completed(val file: File) : DownloadState()
        data class Error(val message: String, val exception: Exception? = null) : DownloadState()
        object Cancelled : DownloadState()
    }
    
    @Volatile
    private var isCancelled = false
    
    /**
     * Download a file from a URL with progress tracking.
     * 
     * @param url The URL to download from
     * @param destinationFile The file to save to
     * @return Flow of download states
     */
    fun download(url: String, destinationFile: File): Flow<DownloadState> = flow {
        isCancelled = false
        emit(DownloadState.Downloading(0, 0, 0))
        
        var connection: HttpURLConnection? = null
        var outputStream: FileOutputStream? = null
        val tempFile = File(destinationFile.parent, "${destinationFile.name}.tmp")
        
        try {
            // Create parent directory if needed
            destinationFile.parentFile?.mkdirs()
            
            // Handle redirects manually for HuggingFace
            var currentUrl = url
            var redirectCount = 0
            val maxRedirects = 5
            
            while (redirectCount < maxRedirects) {
                val urlObj = URL(currentUrl)
                connection = if (currentUrl.startsWith("https")) {
                    urlObj.openConnection() as HttpsURLConnection
                } else {
                    urlObj.openConnection() as HttpURLConnection
                }
                
                connection.apply {
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = READ_TIMEOUT
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Xirea-Android/1.0")
                    connect()
                }
                
                val responseCode = connection.responseCode
                
                if (responseCode in 300..399) {
                    val newUrl = connection.getHeaderField("Location")
                    if (newUrl == null) {
                        emit(DownloadState.Error("Redirect without location header"))
                        return@flow
                    }
                    connection.disconnect()
                    currentUrl = if (newUrl.startsWith("http")) newUrl else URL(URL(currentUrl), newUrl).toString()
                    redirectCount++
                    Log.d(TAG, "Redirecting to: $currentUrl")
                } else if (responseCode == HttpURLConnection.HTTP_OK) {
                    break
                } else {
                    emit(DownloadState.Error("HTTP error: $responseCode"))
                    return@flow
                }
            }
            
            if (redirectCount >= maxRedirects) {
                emit(DownloadState.Error("Too many redirects"))
                return@flow
            }
            
            val totalBytes = connection!!.contentLengthLong
            Log.d(TAG, "Starting download, total size: $totalBytes bytes")
            
            if (totalBytes <= 0) {
                Log.w(TAG, "Content length unknown, progress will be estimated")
            }
            
            // Check for cancellation before starting
            if (isCancelled) {
                emit(DownloadState.Cancelled)
                return@flow
            }
            
            outputStream = FileOutputStream(tempFile)
            val inputStream = connection.inputStream
            val buffer = ByteArray(BUFFER_SIZE)
            var downloadedBytes = 0L
            var bytesRead: Int
            var lastProgressUpdate = 0
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (isCancelled) {
                    inputStream.close()
                    outputStream.close()
                    tempFile.delete()
                    emit(DownloadState.Cancelled)
                    return@flow
                }
                
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                
                // Calculate progress
                val progress = if (totalBytes > 0) {
                    ((downloadedBytes * 100) / totalBytes).toInt()
                } else {
                    // Estimate based on expected file size
                    (downloadedBytes / 10_000_000).toInt().coerceAtMost(99)
                }
                
                // Only emit progress updates when there's a meaningful change
                if (progress > lastProgressUpdate) {
                    lastProgressUpdate = progress
                    emit(DownloadState.Downloading(progress, downloadedBytes, totalBytes))
                }
            }
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            // Rename temp file to final destination
            if (tempFile.renameTo(destinationFile)) {
                Log.d(TAG, "Download completed: ${destinationFile.absolutePath}")
                emit(DownloadState.Completed(destinationFile))
            } else {
                // Try copy if rename fails (different filesystems)
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
                emit(DownloadState.Completed(destinationFile))
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "Download failed", e)
            tempFile.delete()
            emit(DownloadState.Error("Download failed: ${e.message}", e))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during download", e)
            tempFile.delete()
            emit(DownloadState.Error("Unexpected error: ${e.message}", e))
        } finally {
            try {
                outputStream?.close()
            } catch (e: Exception) {
                // Ignore
            }
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Cancel the current download.
     */
    fun cancel() {
        isCancelled = true
    }
    
    /**
     * Check if there's enough storage space for a download.
     * 
     * @param requiredBytes The number of bytes needed
     * @return true if there's enough space
     */
    fun hasEnoughStorage(requiredBytes: Long): Boolean {
        val availableSpace = context.filesDir.freeSpace
        // Require at least 100MB extra space beyond the file size
        return availableSpace > requiredBytes + (100 * 1024 * 1024)
    }
    
    /**
     * Get the available storage space in bytes.
     */
    fun getAvailableStorage(): Long {
        return context.filesDir.freeSpace
    }
}
