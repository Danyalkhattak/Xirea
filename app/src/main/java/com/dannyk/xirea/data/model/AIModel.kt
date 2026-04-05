package com.dannyk.xirea.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_models")
data class AIModel(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val fileName: String,
    val downloadUrl: String,
    val fileSize: Long, // in bytes
    val isDownloaded: Boolean = false,
    val downloadProgress: Int = 0, // 0-100
    val isDownloading: Boolean = false,
    val version: String = "1.0"
)

enum class ModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    LOADING,
    LOADED,
    ERROR
}
