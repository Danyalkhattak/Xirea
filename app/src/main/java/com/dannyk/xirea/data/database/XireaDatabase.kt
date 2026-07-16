package com.dannyk.xirea.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dannyk.xirea.data.dao.AIModelDao
import com.dannyk.xirea.data.dao.ChatDao
import com.dannyk.xirea.data.dao.MessageDao
import com.dannyk.xirea.data.model.AIModel
import com.dannyk.xirea.data.model.Chat
import com.dannyk.xirea.data.model.Message

@Database(
    entities = [Chat::class, Message::class, AIModel::class],
    version = 2,
    exportSchema = false
)
abstract class XireaDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun aiModelDao(): AIModelDao
    
    companion object {
        @Volatile
        private var INSTANCE: XireaDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_models ADD COLUMN isLocalModel INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        fun getDatabase(context: Context): XireaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    XireaDatabase::class.java,
                    "xirea_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
