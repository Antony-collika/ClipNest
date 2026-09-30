package com.clipnest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.clipnest.data.model.Note
import com.clipnest.ui.note.NoteCardProjection
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): Note?

    @Query("""
        SELECT n.id, n.title, n.content, n.updatedAtMillis,
               COALESCE(GROUP_CONCAT(t.name, ', '), '') AS topicLabels
        FROM notes n
        LEFT JOIN note_topic_cross_ref r ON r.noteId = n.id AND r.role = 'USER_TAG'
        LEFT JOIN topics t ON t.id = r.topicId
        WHERE n.isDeleted = 0 AND n.isArchived = 0
        GROUP BY n.id
        ORDER BY n.updatedAtMillis DESC
    """)
    fun observeActiveNoteCards(): Flow<List<NoteCardProjection>>

    @Query("SELECT n.* FROM notes n INNER JOIN note_topic_cross_ref r ON r.noteId = n.id INNER JOIN topics t ON t.id = r.topicId WHERE r.topicId = :topicId AND t.origin = :origin AND n.isDeleted = 0 AND n.isArchived = 0 ORDER BY n.updatedAtMillis DESC")
    fun observeActiveNotesByTopic(topicId: Long, origin: String): Flow<List<Note>>

    @Query("""
        SELECT n.id, n.title, n.content, n.updatedAtMillis,
               COALESCE(GROUP_CONCAT(t.name, ', '), '') AS topicLabels
        FROM notes n
        INNER JOIN note_topic_cross_ref selectedRef
            ON selectedRef.noteId = n.id AND selectedRef.topicId = :topicId
        LEFT JOIN note_topic_cross_ref r
            ON r.noteId = n.id AND r.role = 'USER_TAG'
        LEFT JOIN topics t ON t.id = r.topicId
        WHERE n.isDeleted = 0 AND n.isArchived = 0
        GROUP BY n.id
        ORDER BY n.updatedAtMillis DESC
    """)
    fun observeActiveNoteCardsByTopic(topicId: Long): Flow<List<NoteCardProjection>>

    @Query("SELECT DISTINCT n.* FROM notes n INNER JOIN note_topic_cross_ref r ON r.noteId = n.id INNER JOIN topics t ON t.id = r.topicId WHERE t.origin = :origin AND (t.id = :topicId OR t.parentId = :topicId) AND n.isDeleted = 0 AND n.isArchived = 0 ORDER BY n.updatedAtMillis DESC")
    fun observeActiveNotesByTopicTree(topicId: Long, origin: String): Flow<List<Note>>

    @Query("SELECT * FROM topics WHERE origin = :origin ORDER BY name COLLATE NOCASE ASC")
    fun observeTopicsForOrigin(origin: String): Flow<List<com.clipnest.data.model.Topic>>

    @Query("UPDATE notes SET title = :title, content = :content, updatedAtMillis = :now, editSessionCount = editSessionCount + 1, lastAuthoredAtMillis = :now WHERE id = :id")
    suspend fun updateContentAndBumpEditSession(id: Long, title: String, content: String, now: Long)

    @Query("UPDATE notes SET isDeleted = :isDeleted, deletedAtMillis = :deletedAtMillis, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun setDeleted(id: Long, isDeleted: Boolean, deletedAtMillis: Long?, updatedAtMillis: Long)

    @Query("UPDATE notes SET isArchived = :isArchived, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun setArchived(id: Long, isArchived: Boolean, updatedAtMillis: Long)

    @Query("UPDATE note_topic_cross_ref SET isPinned = :isPinned WHERE noteId = :noteId AND topicId = :topicId AND role = :role")
    suspend fun setTopicPinned(noteId: Long, topicId: Long, role: com.clipnest.data.model.NoteTopicRole, isPinned: Boolean)

    @Query("SELECT EXISTS(SELECT 1 FROM note_topic_cross_ref WHERE noteId = :noteId AND topicId = :topicId AND role = :role AND isPinned = 1)")
    suspend fun isTopicPinned(noteId: Long, topicId: Long, role: com.clipnest.data.model.NoteTopicRole): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM note_topic_cross_ref WHERE noteId = :noteId AND topicId = :topicId AND role = :role)")
    suspend fun hasTopicRelation(noteId: Long, topicId: Long, role: com.clipnest.data.model.NoteTopicRole): Boolean

    @Query("SELECT r.noteId FROM note_topic_cross_ref r WHERE r.topicId = :topicId AND r.role = :role AND r.isPinned = 1")
    fun observePinnedNoteIdsForTopic(topicId: Long, role: com.clipnest.data.model.NoteTopicRole): Flow<List<Long>>

    @Query("UPDATE note_topic_cross_ref SET isPinned = :isPinned WHERE topicId = :topicId AND role = :role AND noteId IN (:noteIds)")
    suspend fun setTopicPinnedForNotes(noteIds: List<Long>, topicId: Long, role: com.clipnest.data.model.NoteTopicRole, isPinned: Boolean)

    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY deletedAtMillis DESC")
    fun observeDeletedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isArchived = 1 AND isDeleted = 0 ORDER BY updatedAtMillis DESC")
    fun observeArchivedNotes(): Flow<List<Note>>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNotePermanently(id: Long)
}
