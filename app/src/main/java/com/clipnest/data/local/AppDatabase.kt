package com.clipnest.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.clipnest.data.model.ClipboardCard
import com.clipnest.data.model.Note
import com.clipnest.data.model.NoteTopicCrossRef
import com.clipnest.data.model.Topic

@Database(
    entities = [ClipboardCard::class, Note::class, Topic::class, NoteTopicCrossRef::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao
    abstract fun noteDao(): NoteDao
    abstract fun topicDao(): TopicDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notes` (
                        `id` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `isPinned` INTEGER NOT NULL,
                        `isArchived` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        `deletedAtMillis` INTEGER,
                        `editSessionCount` INTEGER NOT NULL,
                        `lastAuthoredAtMillis` INTEGER,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `topics` (
                        `id` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `parentId` INTEGER,
                        `origin` TEXT NOT NULL,
                        `level` TEXT NOT NULL,
                        `icon` TEXT,
                        `isPinned` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`parentId`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_topics_parentId` ON `topics` (`parentId`)"
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `note_topic_cross_ref` (
                        `noteId` INTEGER NOT NULL,
                        `topicId` INTEGER NOT NULL,
                        `role` TEXT NOT NULL,
                        `rank` INTEGER,
                        PRIMARY KEY(`noteId`, `topicId`, `role`),
                        FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`topicId`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_note_topic_cross_ref_noteId` ON `note_topic_cross_ref` (`noteId`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_note_topic_cross_ref_topicId` ON `note_topic_cross_ref` (`topicId`)"
                )
            }
        }

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
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun applicationContext(): Context =
            APPLICATION_CONTEXT ?: error("AppDatabase has not been initialized")
    }
}
