package com.dannyk.xirea.data.dao

import androidx.room.*
import com.dannyk.xirea.data.model.AIModel
import kotlinx.coroutines.flow.Flow

@Dao
interface AIModelDao {
    @Query("SELECT * FROM ai_models ORDER BY name ASC")
    fun getAllModels(): Flow<List<AIModel>>
    
    @Query("SELECT * FROM ai_models WHERE isDownloaded = 1")
    fun getDownloadedModels(): Flow<List<AIModel>>
    
    @Query("SELECT * FROM ai_models WHERE id = :modelId")
    suspend fun getModelById(modelId: String): AIModel?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: AIModel)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<AIModel>)
    
    @Update
    suspend fun updateModel(model: AIModel)
    
    @Delete
    suspend fun deleteModel(model: AIModel)
    
    @Query("UPDATE ai_models SET isDownloaded = :isDownloaded WHERE id = :modelId")
    suspend fun updateDownloadStatus(modelId: String, isDownloaded: Boolean)
    
    @Query("UPDATE ai_models SET downloadProgress = :progress, isDownloading = :isDownloading WHERE id = :modelId")
    suspend fun updateDownloadProgress(modelId: String, progress: Int, isDownloading: Boolean)
}
