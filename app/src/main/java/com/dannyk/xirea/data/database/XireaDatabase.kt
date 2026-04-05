package com.dannyk.xirea.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dannyk.xirea.data.dao.AIModelDao
import com.dannyk.xirea.data.dao.ChatDao
import com.dannyk.xirea.data.dao.MessageDao
import com.dannyk.xirea.data.model.AIModel
import com.dannyk.xirea.data.model.Chat
import com.dannyk.xirea.data.model.Message

@Database(
    entities = [Chat::class, Message::class, AIModel::class],
    version = 1,
    exportSchema = false
)
abstract class XireaDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun aiModelDao(): AIModelDao
    
    companion object {
        @Volatile
        private var INSTANCE: XireaDatabase? = null
        
        fun getDatabase(context: Context): XireaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    XireaDatabase::class.java,
                    "xirea_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
