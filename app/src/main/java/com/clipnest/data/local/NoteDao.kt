package com.clipnest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.clipnest.data.model.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): Note?

    @Query(
        """
        SELECT * FROM notes
        WHERE isDeleted = 0 AND isArchived = 0
        ORDER BY isPinned DESC, updatedAtMillis DESC
        """
    )
    fun observeActiveNotes(): Flow<List<Note>>

    @Query(
        """
        SELECT n.* FROM notes n
        INNER JOIN note_topic_cross_ref r ON r.noteId = n.id
        WHERE r.topicId = :topicId
          AND n.isDeleted = 0
          AND n.isArchived = 0
        ORDER BY n.isPinned DESC, n.updatedAtMillis DESC
        """
    )
    fun observeActiveNotesByTopic(topicId: Long): Flow<List<Note>>

    @Query(
        """
        UPDATE notes
        SET title = :title,
            content = :content,
            updatedAtMillis = :now,
            editSessionCount = editSessionCount + 1,
            lastAuthoredAtMillis = :now
        WHERE id = :id
        """
    )
    suspend fun updateContentAndBumpEditSession(
        id: Long,
        title: String,
        content: String,
        now: Long
    )

    @Query(
        """
        UPDATE notes
        SET isDeleted = :isDeleted,
            deletedAtMillis = :deletedAtMillis,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """
    )
    suspend fun setDeleted(
        id: Long,
        isDeleted: Boolean,
        deletedAtMillis: Long?,
        updatedAtMillis: Long
    )

    @Query(
        """
        UPDATE notes
        SET isArchived = :isArchived,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """
    )
    suspend fun setArchived(id: Long, isArchived: Boolean, updatedAtMillis: Long)

    @Query(
        """
        UPDATE notes
        SET isPinned = :isPinned,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """
    )
    suspend fun setPinned(id: Long, isPinned: Boolean, updatedAtMillis: Long)

    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY deletedAtMillis DESC")
    fun observeDeletedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isArchived = 1 AND isDeleted = 0 ORDER BY updatedAtMillis DESC")
    fun observeArchivedNotes(): Flow<List<Note>>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNotePermanently(id: Long)
}
