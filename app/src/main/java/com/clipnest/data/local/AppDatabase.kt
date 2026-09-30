package com.clipnest.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.clipnest.data.model.ClipboardCard
import com.clipnest.data.model.ClipboardCardFts
import com.clipnest.data.model.Note
import com.clipnest.data.model.NoteFts
import com.clipnest.data.model.NoteTopicCrossRef
import com.clipnest.data.model.Topic

@Database(
    entities = [
        ClipboardCard::class,
        ClipboardCardFts::class,
        Note::class,
        NoteFts::class,
        Topic::class,
        NoteTopicCrossRef::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao
    abstract fun noteDao(): NoteDao
    abstract fun topicDao(): TopicDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        @Volatile
        private var APPLICATION_CONTEXT: Context? = null

        fun getInstance(context: Context): AppDatabase {
            APPLICATION_CONTEXT = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "clipboard_vault.db"
                )
                    // ClipNest is pre-release; resetting the local database is acceptable.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun applicationContext(): Context =
            APPLICATION_CONTEXT ?: error("AppDatabase has not been initialized")
    }
}
