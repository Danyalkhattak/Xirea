package com.dannyk.xirea.ui.models

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dannyk.xirea.ai.AIEngine
import com.dannyk.xirea.data.download.DownloadStatus
import com.dannyk.xirea.data.download.ModelDownloader
import com.dannyk.xirea.data.model.AIModel
import com.dannyk.xirea.data.model.ModelStatus
import com.dannyk.xirea.data.preferences.UserPreferences
import com.dannyk.xirea.data.repository.ModelRepository
import com.dannyk.xirea.service.DownloadService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ModelsUiState(
    val models: List<AIModel> = emptyList(),
    val selectedModelId: String? = null,
    val loadingModelId: String? = null,
    val downloadingModelId: String? = null,
    val downloadProgress: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val totalStorageUsed: Long = 0,
    val availableStorage: Long = 0,
    val isLowStorage: Boolean = false,
    val error: String? = null,
    val showDeleteDialog: Boolean = false,
    val modelToDelete: AIModel? = null,
    val deviceTotalRamMB: Long = 4096
)

class ModelsViewModel(
    private val modelRepository: ModelRepository,
    private val userPreferences: UserPreferences,
    private val aiEngine: AIEngine,
    private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ModelsUiState())
    val uiState: StateFlow<ModelsUiState> = _uiState.asStateFlow()
    
    private val modelDownloader = ModelDownloader(context)
    private var downloadJob: Job? = null
    
    // Broadcast receiver for download service updates
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                DownloadService.ACTION_DOWNLOAD_PROGRESS -> {
                    val progress = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0)
                    val bytesDownloaded = intent.getLongExtra(DownloadService.EXTRA_BYTES_DOWNLOADED, 0)
                    val totalBytes = intent.getLongExtra(DownloadService.EXTRA_TOTAL_BYTES, 0)
                    
                    _uiState.update { 
                        it.copy(
                            downloadProgress = progress,
                            downloadedBytes = bytesDownloaded,
                            totalBytes = totalBytes
                        )
                    }
                    
                    // Update repository
                    _uiState.value.downloadingModelId?.let { modelId ->
                        viewModelScope.launch {
                            modelRepository.updateDownloadProgress(modelId, progress, true)
                        }
                    }
                }
                DownloadService.ACTION_DOWNLOAD_COMPLETE -> {
                    val fileName = intent.getStringExtra(DownloadService.EXTRA_FILE_NAME)
                    _uiState.value.downloadingModelId?.let { modelId ->
                        viewModelScope.launch {
                            modelRepository.updateDownloadStatus(modelId, true)
                            modelRepository.updateDownloadProgress(modelId, 100, false)
                            updateStorageInfo()
                        }
                    }
                    _uiState.update { 
                        it.copy(
                            downloadingModelId = null,
                            downloadProgress = 0,
                            downloadedBytes = 0,
                            totalBytes = 0
                        )
                    }
                }
                DownloadService.ACTION_DOWNLOAD_FAILED -> {
                    _uiState.value.downloadingModelId?.let { modelId ->
                        viewModelScope.launch {
                            modelRepository.updateDownloadProgress(modelId, 0, false)
                        }
                    }
                    _uiState.update { 
                        it.copy(
                            downloadingModelId = null,
                            downloadProgress = 0,
                            downloadedBytes = 0,
                            totalBytes = 0,
                            error = "Download failed. Please check your internet connection and try again."
                        )
                    }
                }
            }
        }
    }
    
    init {
        loadModels()
        loadSelectedModel()
        updateStorageInfo()
        registerDownloadReceiver()
        restoreDownloadState()
        detectDeviceRam()
    }
    
    private fun detectDeviceRam() {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val totalRamMB = memInfo.totalMem / (1024 * 1024)
            _uiState.update { it.copy(deviceTotalRamMB = totalRamMB) }
        } catch (_: Exception) {
            _uiState.update { it.copy(deviceTotalRamMB = 4096) }
        }
    }
    
    /**
     * Restore download state if a download was in progress
     */
    private fun restoreDownloadState() {
        val downloadingModelId = DownloadService.getDownloadingModelId(context)
        if (downloadingModelId != null) {
            val progress = DownloadService.getDownloadProgress(context)
            _uiState.update { 
                it.copy(
                    downloadingModelId = downloadingModelId,
                    downloadProgress = progress
                )
            }
        }
    }
    
    private fun registerDownloadReceiver() {
        val filter = IntentFilter().apply {
            addAction(DownloadService.ACTION_DOWNLOAD_PROGRESS)
            addAction(DownloadService.ACTION_DOWNLOAD_COMPLETE)
            addAction(DownloadService.ACTION_DOWNLOAD_FAILED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(downloadReceiver, filter)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }
    
    private fun loadModels() {
        viewModelScope.launch {
            modelRepository.initializeDefaultModels()
            modelRepository.allModels.collect { models ->
                _uiState.update { it.copy(models = models) }
            }
        }
    }
    
    private fun loadSelectedModel() {
        viewModelScope.launch {
            userPreferences.selectedModelId.collect { modelId ->
                _uiState.update { it.copy(selectedModelId = modelId) }
            }
        }
    }
    
    private fun updateStorageInfo() {
        viewModelScope.launch {
            val totalUsed = modelRepository.getModelsStorageSize()
            val available = modelRepository.getAvailableStorage()
            val isLow = available < 500 * 1024 * 1024 // Less than 500MB
            _uiState.update { 
                it.copy(
                    totalStorageUsed = totalUsed,
                    availableStorage = available,
                    isLowStorage = isLow
                )
            }
        }
    }
    
    fun selectModel(model: AIModel) {
        if (!model.isDownloaded) {
            _uiState.update { it.copy(error = "Please download the model first") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(loadingModelId = model.id) }
            
            val modelFile = modelRepository.getModelFile(model)
            val result = aiEngine.loadModel(model, modelFile)
            
            if (result.isSuccess) {
                userPreferences.setSelectedModel(model.id)
                _uiState.update { it.copy(loadingModelId = null) }
            } else {
                _uiState.update { 
                    it.copy(
                        loadingModelId = null,
                        error = "Failed to load model: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }
    
    fun downloadModel(model: AIModel) {
        if (_uiState.value.downloadingModelId != null) {
            _uiState.update { it.copy(error = "Another download is in progress") }
            return
        }
        
        if (_uiState.value.isLowStorage) {
            _uiState.update { it.copy(error = "Not enough storage space") }
            return
        }
        
        // Check if available storage is sufficient for the model
        if (_uiState.value.availableStorage < model.fileSize) {
            _uiState.update { 
                it.copy(error = "Not enough storage space. Need ${model.fileSize / (1024 * 1024)}MB") 
            }
            return
        }
        
        // Update UI state to show downloading
        _uiState.update { 
            it.copy(
                downloadingModelId = model.id, 
                downloadProgress = 0,
                downloadedBytes = 0,
                totalBytes = model.fileSize
            ) 
        }
        
        viewModelScope.launch {
            modelRepository.updateDownloadProgress(model.id, 0, true)
        }
        
        // Start foreground service for background-safe downloading
        DownloadService.startDownload(
            context = context,
            downloadUrl = model.downloadUrl,
            fileName = model.fileName,
            modelName = model.name,
            modelId = model.id
        )
    }
    
    fun cancelDownload() {
        val downloadingId = _uiState.value.downloadingModelId ?: return
        
        // Cancel via the service
        DownloadService.cancelDownload(context)
        
        viewModelScope.launch {
            modelRepository.updateDownloadProgress(downloadingId, 0, false)
            // Delete any partial download
            val model = _uiState.value.models.find { it.id == downloadingId }
            model?.let { modelDownloader.deleteModelFile(it.fileName) }
            
            _uiState.update { 
                it.copy(
                    downloadingModelId = null,
                    downloadProgress = 0,
                    downloadedBytes = 0,
                    totalBytes = 0
                )
            }
        }
    }
    
    fun showDeleteDialog(model: AIModel) {
        _uiState.update { it.copy(showDeleteDialog = true, modelToDelete = model) }
    }
    
    fun hideDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false, modelToDelete = null) }
    }
    
    fun deleteModel() {
        val model = _uiState.value.modelToDelete ?: return
        
        viewModelScope.launch {
            // Unload if currently loaded
            if (_uiState.value.selectedModelId == model.id) {
                aiEngine.unloadModel()
                userPreferences.setSelectedModel(null)
            }
            
            modelRepository.deleteModel(model)
            updateStorageInfo()
            hideDeleteDialog()
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    class Factory(
        private val modelRepository: ModelRepository,
        private val userPreferences: UserPreferences,
        private val aiEngine: AIEngine,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ModelsViewModel::class.java)) {
                return ModelsViewModel(modelRepository, userPreferences, aiEngine, context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
