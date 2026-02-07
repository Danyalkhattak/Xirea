package com.dannyk.xirea

import android.app.Application
import com.dannyk.xirea.ai.AIEngine
import com.dannyk.xirea.data.database.XireaDatabase
import com.dannyk.xirea.data.download.ModelDownloader
import com.dannyk.xirea.data.preferences.UserPreferences
import com.dannyk.xirea.data.repository.ChatRepository
import com.dannyk.xirea.data.repository.ModelRepository

class XireaApplication : Application() {
    
    // Database
    private val database by lazy { XireaDatabase.getDatabase(this) }
    
    // DAOs
    private val chatDao by lazy { database.chatDao() }
    private val messageDao by lazy { database.messageDao() }
    private val aiModelDao by lazy { database.aiModelDao() }
    
    // Repositories
    val chatRepository by lazy { ChatRepository(chatDao, messageDao) }
    val modelRepository by lazy { ModelRepository(aiModelDao, this) }
    
    // Preferences
    val userPreferences by lazy { UserPreferences(this) }
    
    // AI Engine (singleton) - now with context for memory-aware optimizations
    val aiEngine by lazy { AIEngine(this) }
    
    // Model Downloader
    val modelDownloader by lazy { ModelDownloader(this) }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        private lateinit var instance: XireaApplication
        
        fun getInstance(): XireaApplication = instance
    }
}
