package com.dannyk.xirea.data.repository

import android.content.Context
import com.dannyk.xirea.data.dao.AIModelDao
import com.dannyk.xirea.data.model.AIModel
import kotlinx.coroutines.flow.Flow
import java.io.File

class ModelRepository(
    private val aiModelDao: AIModelDao,
    private val context: Context
) {
    val allModels: Flow<List<AIModel>> = aiModelDao.getAllModels()
    val downloadedModels: Flow<List<AIModel>> = aiModelDao.getDownloadedModels()
    
    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }
    
    suspend fun getModelById(modelId: String): AIModel? {
        return aiModelDao.getModelById(modelId)
    }
    
    suspend fun insertModel(model: AIModel) {
        aiModelDao.insertModel(model)
    }
    
    suspend fun insertModels(models: List<AIModel>) {
        aiModelDao.insertModels(models)
    }
    
    suspend fun updateDownloadStatus(modelId: String, isDownloaded: Boolean) {
        aiModelDao.updateDownloadStatus(modelId, isDownloaded)
    }
    
    suspend fun updateDownloadProgress(modelId: String, progress: Int, isDownloading: Boolean) {
        aiModelDao.updateDownloadProgress(modelId, progress, isDownloading)
    }
    
    suspend fun deleteModel(model: AIModel) {
        // Delete the model file
        val modelFile = File(modelsDir, model.fileName)
        if (modelFile.exists()) {
            modelFile.delete()
        }
        // Update database
        aiModelDao.updateDownloadStatus(model.id, false)
    }
    
    fun getModelFile(model: AIModel): File {
        return File(modelsDir, model.fileName)
    }
    
    fun getModelsStorageSize(): Long {
        return modelsDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }
    
    fun getAvailableStorage(): Long {
        return context.filesDir.freeSpace
    }
    
    suspend fun initializeDefaultModels() {
        // Check if models already exist
        val existingModels = aiModelDao.getModelById("tinyllama-1.1b")
        if (existingModels == null) {
            val defaultModels = listOf(
                AIModel(
                    id = "tinyllama-1.1b",
                    name = "TinyLlama 1.1B",
                    description = "A compact but capable language model, great for basic conversations",
                    fileName = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
                    downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
                    fileSize = 669_000_000L, // ~669 MB
                    version = "1.0"
                ),
                AIModel(
                    id = "phi-2",
                    name = "Phi-2 2.7B",
                    description = "Microsoft's efficient model with strong reasoning capabilities",
                    fileName = "phi-2.Q4_K_M.gguf",
                    downloadUrl = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf",
                    fileSize = 1_600_000_000L, // ~1.6 GB
                    version = "1.0"
                ),
                AIModel(
                    id = "gemma-2b",
                    name = "Gemma 2B",
                    description = "Google's lightweight model optimized for mobile devices",
                    fileName = "gemma-2b-it.Q4_K_M.gguf",
                    downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
                    fileSize = 1_500_000_000L, // ~1.5 GB
                    version = "1.0"
                ),
                AIModel(
                    id = "qwen2-0.5b",
                    name = "Qwen2 0.5B",
                    description = "Ultra-lightweight model for quick responses on any device",
                    fileName = "qwen2-0_5b-instruct-q4_k_m.gguf",
                    downloadUrl = "https://huggingface.co/Qwen/Qwen2-0.5B-Instruct-GGUF/resolve/main/qwen2-0_5b-instruct-q4_k_m.gguf",
                    fileSize = 400_000_000L, // ~400 MB
                    version = "1.0"
                )
            )
            aiModelDao.insertModels(defaultModels)
        }
    }
}
