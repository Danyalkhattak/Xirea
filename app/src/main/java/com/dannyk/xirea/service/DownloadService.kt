package com.dannyk.xirea.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dannyk.xirea.MainActivity
import com.dannyk.xirea.R
import com.dannyk.xirea.data.download.DownloadProgress
import com.dannyk.xirea.data.download.DownloadStatus
import com.dannyk.xirea.data.download.ModelDownloader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Foreground service for downloading AI models.
 * Continues downloading even when the app is in background.
 */
class DownloadService : Service() {
    
    companion object {
        private const val TAG = "DownloadService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "download_channel"
        private const val CHANNEL_NAME = "Model Downloads"
        private const val PREFS_NAME = "download_prefs"
        private const val PREF_DOWNLOADING_MODEL_ID = "downloading_model_id"
        private const val PREF_DOWNLOADING_FILE_NAME = "downloading_file_name"
        private const val PREF_DOWNLOAD_PROGRESS = "download_progress"
        
        // Intent extras
        const val EXTRA_DOWNLOAD_URL = "download_url"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_MODEL_NAME = "model_name"
        const val EXTRA_MODEL_ID = "model_id"
        
        // Broadcast actions
        const val ACTION_DOWNLOAD_PROGRESS = "com.dannyk.xirea.DOWNLOAD_PROGRESS"
        const val ACTION_DOWNLOAD_COMPLETE = "com.dannyk.xirea.DOWNLOAD_COMPLETE"
        const val ACTION_DOWNLOAD_FAILED = "com.dannyk.xirea.DOWNLOAD_FAILED"
        const val ACTION_CANCEL_DOWNLOAD = "com.dannyk.xirea.CANCEL_DOWNLOAD"
        
        // Broadcast extras
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_BYTES_DOWNLOADED = "bytes_downloaded"
        const val EXTRA_TOTAL_BYTES = "total_bytes"
        const val EXTRA_STATUS = "status"
        
        fun startDownload(context: Context, downloadUrl: String, fileName: String, modelName: String, modelId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_DOWNLOAD_URL, downloadUrl)
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_MODEL_NAME, modelName)
                putExtra(EXTRA_MODEL_ID, modelId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun cancelDownload(context: Context) {
            // Clear persisted state
            getPrefs(context).edit().clear().apply()
            
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
            }
            context.startService(intent)
        }
        
        private fun getPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        
        /**
         * Get the currently downloading model ID, if any
         */
        fun getDownloadingModelId(context: Context): String? {
            return getPrefs(context).getString(PREF_DOWNLOADING_MODEL_ID, null)
        }
        
        /**
         * Get the current download progress (0-100)
         */
        fun getDownloadProgress(context: Context): Int {
            return getPrefs(context).getInt(PREF_DOWNLOAD_PROGRESS, 0)
        }
        
        /**
         * Check if a download is in progress
         */
        fun isDownloading(context: Context): Boolean {
            return getDownloadingModelId(context) != null
        }
    }
    
    private val binder = DownloadBinder()
    private var downloader: ModelDownloader? = null
    private var downloadJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()
    
    private var currentModelName: String = ""
    private var currentModelId: String = ""
    private var currentFileName: String = ""
    private var isDownloading = false
    
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    inner class DownloadBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        downloader = ModelDownloader(applicationContext)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_DOWNLOAD -> {
                cancelCurrentDownload()
                return START_NOT_STICKY
            }
        }
        
        val downloadUrl = intent?.getStringExtra(EXTRA_DOWNLOAD_URL)
        val fileName = intent?.getStringExtra(EXTRA_FILE_NAME)
        val modelName = intent?.getStringExtra(EXTRA_MODEL_NAME) ?: "Model"
        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID) ?: ""
        
        if (downloadUrl != null && fileName != null) {
            startDownload(downloadUrl, fileName, modelName, modelId)
        }
        
        return START_STICKY
    }
    
    private fun startDownload(downloadUrl: String, fileName: String, modelName: String, modelId: String) {
        if (isDownloading) {
            Log.w(TAG, "Download already in progress")
            return
        }
        
        isDownloading = true
        currentModelName = modelName
        currentModelId = modelId
        currentFileName = fileName
        
        // Persist download state
        prefs.edit()
            .putString(PREF_DOWNLOADING_MODEL_ID, modelId)
            .putString(PREF_DOWNLOADING_FILE_NAME, fileName)
            .putInt(PREF_DOWNLOAD_PROGRESS, 0)
            .apply()
        
        // Acquire wake lock to keep CPU running during download
        acquireWakeLock()
        
        // Start foreground with notification
        startForeground(NOTIFICATION_ID, createNotification(0, "Starting download..."))
        
        downloadJob = serviceScope.launch {
            try {
                downloader?.downloadModel(downloadUrl, fileName)?.collect { progress ->
                    _downloadProgress.value = progress
                    
                    // Update persisted progress
                    prefs.edit().putInt(PREF_DOWNLOAD_PROGRESS, progress.progress).apply()
                    
                    when (progress.status) {
                        DownloadStatus.STARTING -> {
                            updateNotification(0, "Starting download...")
                        }
                        DownloadStatus.DOWNLOADING -> {
                            updateNotification(progress.progress, "Downloading $currentModelName")
                            broadcastProgress(progress)
                        }
                        DownloadStatus.COMPLETED -> {
                            // Clear persisted state on completion
                            prefs.edit().clear().apply()
                            showCompletedNotification()
                            broadcastComplete(fileName)
                            stopSelf()
                        }
                        DownloadStatus.FAILED -> {
                            // Clear persisted state on failure
                            prefs.edit().clear().apply()
                            showFailedNotification()
                            broadcastFailed()
                            stopSelf()
                        }
                        DownloadStatus.CANCELLED -> {
                            prefs.edit().clear().apply()
                            stopSelf()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                showFailedNotification()
                broadcastFailed()
                stopSelf()
            }
        }
    }
    
    private fun cancelCurrentDownload() {
        downloadJob?.cancel()
        downloader?.cancelDownload()
        isDownloading = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for model download progress"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(progress: Int, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val cancelIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading $currentModelName")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
    
    private fun updateNotification(progress: Int, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading $currentModelName")
            .setContentText("$progress% complete")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, DownloadService::class.java).apply {
                        action = ACTION_CANCEL_DOWNLOAD
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun showCompletedNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText("$currentModelName is ready to use")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
    
    private fun showFailedNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("Failed to download $currentModelName")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }
    
    private fun broadcastProgress(progress: DownloadProgress) {
        val intent = Intent(ACTION_DOWNLOAD_PROGRESS).apply {
            putExtra(EXTRA_PROGRESS, progress.progress)
            putExtra(EXTRA_BYTES_DOWNLOADED, progress.bytesDownloaded)
            putExtra(EXTRA_TOTAL_BYTES, progress.totalBytes)
            putExtra(EXTRA_STATUS, progress.status.name)
            `package` = packageName
        }
        sendBroadcast(intent)
    }
    
    private fun broadcastComplete(fileName: String) {
        val intent = Intent(ACTION_DOWNLOAD_COMPLETE).apply {
            putExtra(EXTRA_FILE_NAME, fileName)
            `package` = packageName
        }
        sendBroadcast(intent)
    }
    
    private fun broadcastFailed() {
        val intent = Intent(ACTION_DOWNLOAD_FAILED).apply {
            `package` = packageName
        }
        sendBroadcast(intent)
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Xirea::DownloadWakeLock"
        ).apply {
            acquire(60 * 60 * 1000L) // 1 hour max
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        isDownloading = false
    }
}
