package com.clipnest.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.clipnest.data.model.ClipboardCard

@Database(entities = [ClipboardCard::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        @Volatile
        private var APPLICATION_CONTEXT: Context? = null

        fun getInstance(context: Context): AppDatabase {
            APPLICATION_CONTEXT = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "clipboard_vault.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }

        fun applicationContext(): Context =
            APPLICATION_CONTEXT ?: error("AppDatabase has not been initialized")
    }
}
