package com.clipnest.data.local

import androidx.room.Room
import com.clipnest.data.model.ClipboardCard
import com.clipnest.data.model.ContentType
import com.clipnest.data.model.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class AppDatabaseFtsTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun noteFtsIsCreatedAndSynced() = runBlocking {
        val noteId = database.noteDao().insertNote(
            Note(
                title = "Nguyễn Văn A",
                content = "Nội dung về clipboard và ghi chú",
                createdAtMillis = 1L,
                updatedAtMillis = 1L
            )
        )

        val results = database.noteDao()
            .searchActiveNoteCards(""nguyen van"")
            .first()

        assertTrue(results.any { it.id == noteId })
    }

    @Test
    fun clipboardFtsIsCreatedAndSynced() = runBlocking {
        database.clipboardDao().insertCard(
            ClipboardCard(
                id = 1L,
                content = "Kotlin Android clipboard",
                createdAtMillis = 1L,
                sortOrder = 1L,
                sourceApp = "ClipNest",
                contentType = ContentType.TEXT,
                pinned = false,
                preview = "Kotlin Android clipboard",
                isSensitive = false
            )
        )

        val results = database.clipboardDao()
            .searchCardProjections(""kotlin android"")
            .first()

        assertEquals(1, results.size)
        assertEquals(1L, results.single().id)
    }
}
