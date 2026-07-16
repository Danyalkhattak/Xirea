package com.dannyk.xirea.data.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Download progress data class
 */
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val progress: Int, // 0-100
    val status: DownloadStatus
)

enum class DownloadStatus {
    STARTING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Handles downloading of AI model files with progress tracking.
 */
class ModelDownloader(private val context: Context) {
    
    companion object {
        private const val TAG = "ModelDownloader"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 30000
        private const val READ_TIMEOUT = 60000
    }
    
    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }
    
    @Volatile
    private var isCancelled = false
    
    /**
     * Download a model file with progress updates.
     * 
     * @param downloadUrl URL to download from
     * @param fileName Target filename for the downloaded model
     * @return Flow emitting download progress updates
     */
    fun downloadModel(downloadUrl: String, fileName: String): Flow<DownloadProgress> = flow {
        isCancelled = false
        val targetFile = File(modelsDir, fileName)
        val tempFile = File(modelsDir, "$fileName.tmp")
        
        emit(DownloadProgress(0, 0, 0, DownloadStatus.STARTING))
        
        var connection: HttpURLConnection? = null
        var inputStream: BufferedInputStream? = null
        var outputStream: FileOutputStream? = null
        
        try {
            // Handle redirects for Hugging Face URLs
            var url = URL(downloadUrl)
            var redirectCount = 0
            val maxRedirects = 5
            
            while (redirectCount < maxRedirects) {
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "Xirea-Android/1.0")
                
                val responseCode = connection.responseCode
                
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308) {
                    
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    
                    if (newUrl == null) {
                        emit(DownloadProgress(0, 0, 0, DownloadStatus.FAILED))
                        return@flow
                    }
                    
                    url = URL(newUrl)
                    redirectCount++
                    Log.d(TAG, "Redirecting to: $newUrl")
                } else {
                    break
                }
            }
            
            if (connection == null || connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error: ${connection?.responseCode}")
                emit(DownloadProgress(0, 0, 0, DownloadStatus.FAILED))
                return@flow
            }
            
            val totalBytes = connection.contentLengthLong
            var bytesDownloaded = 0L
            
            Log.d(TAG, "Starting download: $fileName, size: $totalBytes bytes")
            
            inputStream = BufferedInputStream(connection.inputStream, BUFFER_SIZE)
            outputStream = FileOutputStream(tempFile)
            
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var lastProgressUpdate = 0
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (isCancelled) {
                    Log.d(TAG, "Download cancelled")
                    emit(DownloadProgress(bytesDownloaded, totalBytes, 0, DownloadStatus.CANCELLED))
                    cleanupTempFile(tempFile)
                    return@flow
                }
                
                outputStream.write(buffer, 0, bytesRead)
                bytesDownloaded += bytesRead
                
                val progress = if (totalBytes > 0) {
                    ((bytesDownloaded.toDouble() / totalBytes) * 100).roundToInt().coerceIn(0, 100)
                } else {
                    0
                }
                
                // Emit progress updates at meaningful intervals (every 1%)
                if (progress > lastProgressUpdate) {
                    lastProgressUpdate = progress
                    emit(DownloadProgress(bytesDownloaded, totalBytes, progress, DownloadStatus.DOWNLOADING))
                }
            }
            
            outputStream.flush()
            outputStream.close()
            outputStream = null
            
            // Rename temp file to final file
            if (tempFile.exists()) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (tempFile.renameTo(targetFile)) {
                    Log.d(TAG, "Download completed: $fileName")
                    emit(DownloadProgress(bytesDownloaded, totalBytes, 100, DownloadStatus.COMPLETED))
                } else {
                    Log.e(TAG, "Failed to rename temp file")
                    emit(DownloadProgress(bytesDownloaded, totalBytes, 0, DownloadStatus.FAILED))
                }
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "Download error: ${e.message}", e)
            emit(DownloadProgress(0, 0, 0, DownloadStatus.FAILED))
            cleanupTempFile(tempFile)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            emit(DownloadProgress(0, 0, 0, DownloadStatus.FAILED))
            cleanupTempFile(tempFile)
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
                connection?.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing resources: ${e.message}")
            }
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Cancel the current download.
     */
    fun cancelDownload() {
        isCancelled = true
    }
    
    /**
     * Check if a model file exists.
     */
    fun isModelDownloaded(fileName: String): Boolean {
        return File(modelsDir, fileName).exists()
    }
    
    /**
     * Get the model file.
     */
    fun getModelFile(fileName: String): File {
        return File(modelsDir, fileName)
    }
    
    /**
     * Delete a model file.
     */
    fun deleteModelFile(fileName: String): Boolean {
        val file = File(modelsDir, fileName)
        val tempFile = File(modelsDir, "$fileName.tmp")
        
        var success = true
        if (file.exists()) {
            success = file.delete()
        }
        if (tempFile.exists()) {
            tempFile.delete()
        }
        return success
    }
    
    /**
     * Get total size of downloaded models.
     */
    fun getDownloadedModelsSize(): Long {
        return modelsDir.walkTopDown()
            .filter { it.isFile && it.extension == "gguf" }
            .map { it.length() }
            .sum()
    }
    
    private fun cleanupTempFile(tempFile: File) {
        try {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup temp file: ${e.message}")
        }
    }
}
