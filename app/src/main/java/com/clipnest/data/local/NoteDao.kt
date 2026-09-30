package com.clipnest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.SkipQueryVerification
import com.clipnest.data.model.Note
import com.clipnest.data.model.NoteCardProjection
import com.clipnest.domain.SearchTextNormalizer
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): Note?

    @Query("""
        SELECT n.id, n.title, n.preview AS preview, n.updatedAtMillis,
               COALESCE(GROUP_CONCAT(t.name, ', '), '') AS topicLabels
        FROM notes n
        LEFT JOIN note_topic_cross_ref r ON r.noteId = n.id AND r.role = 'USER_TAG'
        LEFT JOIN topics t ON t.id = r.topicId
        WHERE n.isDeleted = 0 AND n.isArchived = 0
        GROUP BY n.id
        ORDER BY n.updatedAtMillis DESC
    """)
    fun observeActiveNoteCards(): Flow<List<NoteCardProjection>>

    @SkipQueryVerification
    @Query("""
        SELECT n.id, n.title, n.preview AS preview, n.updatedAtMillis,
               COALESCE(GROUP_CONCAT(t.name, ', '), '') AS topicLabels
        FROM notes n
        LEFT JOIN note_topic_cross_ref r ON r.noteId = n.id AND r.role = 'USER_TAG'
        LEFT JOIN topics t ON t.id = r.topicId
        WHERE n.isDeleted = 0 AND n.isArchived = 0
          AND n.id IN (SELECT rowid FROM notes_fts WHERE notes_fts MATCH :ftsQuery)
        GROUP BY n.id
        ORDER BY n.updatedAtMillis DESC
    """)
    fun searchActiveNoteCards(ftsQuery: String): Flow<List<NoteCardProjection>>

    @Query("""
        SELECT n.id, n.title, n.preview AS preview, n.updatedAtMillis,
               COALESCE(GROUP_CONCAT(t.name, ', '), '') AS topicLabels
        FROM notes n
        INNER JOIN note_topic_cross_ref r ON r.noteId = n.id AND r.topicId = :topicId
        INNER JOIN topics selectedTopic ON selectedTopic.id = r.topicId AND selectedTopic.origin = :origin
        LEFT JOIN note_topic_cross_ref tagRef ON tagRef.noteId = n.id AND tagRef.role = 'USER_TAG'
        LEFT JOIN topics t ON t.id = tagRef.topicId
        WHERE n.isDeleted = 0 AND n.isArchived = 0
        GROUP BY n.id
        ORDER BY n.updatedAtMillis DESC
    """)
    fun observeActiveNotesByTopic(topicId: Long, origin: String): Flow<List<NoteCardProjection>>

    @Query("""
        SELECT n.id, n.title, n.preview AS preview, n.updatedAtMillis,
               COALESCE(GROUP_CONCAT(t.name, ', '), '') AS topicLabels
        FROM notes n
        INNER JOIN note_topic_cross_ref selectedRef
            ON selectedRef.noteId = n.id AND selectedRef.topicId = :topicId
        INNER JOIN topics selectedTopic
            ON selectedTopic.id = selectedRef.topicId AND selectedTopic.origin = :origin
        LEFT JOIN note_topic_cross_ref r
            ON r.noteId = n.id AND r.role = 'USER_TAG'
        LEFT JOIN topics t ON t.id = r.topicId
        WHERE n.isDeleted = 0 AND n.isArchived = 0
        GROUP BY n.id
        ORDER BY n.updatedAtMillis DESC
    """)
    fun observeActiveNoteCardsByTopic(topicId: Long, origin: String): Flow<List<NoteCardProjection>>

    @Query("""
        SELECT DISTINCT n.id, n.title, n.preview AS preview, n.updatedAtMillis,
               COALESCE((
                   SELECT GROUP_CONCAT(t2.name, ', ')
                   FROM note_topic_cross_ref r2
                   INNER JOIN topics t2 ON t2.id = r2.topicId
                   WHERE r2.noteId = n.id AND r2.role = 'USER_TAG'
               ), '') AS topicLabels
        FROM notes n
        INNER JOIN note_topic_cross_ref selectedRef ON selectedRef.noteId = n.id
        INNER JOIN topics selectedTopic ON selectedTopic.id = selectedRef.topicId
        WHERE selectedTopic.origin = :origin
          AND (selectedTopic.id = :topicId OR selectedTopic.parentId = :topicId)
          AND n.isDeleted = 0 AND n.isArchived = 0
        ORDER BY n.updatedAtMillis DESC
    """)
    fun observeActiveNoteCardsByTopicTree(topicId: Long, origin: String): Flow<List<NoteCardProjection>>

    @SkipQueryVerification
    @Query("""
        SELECT DISTINCT n.id, n.title, n.preview AS preview, n.updatedAtMillis,
               COALESCE((
                   SELECT GROUP_CONCAT(t2.name, ', ')
                   FROM note_topic_cross_ref r2
                   INNER JOIN topics t2 ON t2.id = r2.topicId
                   WHERE r2.noteId = n.id AND r2.role = 'USER_TAG'
               ), '') AS topicLabels
        FROM notes n
        INNER JOIN note_topic_cross_ref selectedRef ON selectedRef.noteId = n.id
        INNER JOIN topics selectedTopic ON selectedTopic.id = selectedRef.topicId
        WHERE selectedTopic.origin = :origin
          AND (selectedTopic.id = :topicId OR selectedTopic.parentId = :topicId)
          AND n.isDeleted = 0 AND n.isArchived = 0
          AND n.id IN (SELECT rowid FROM notes_fts WHERE notes_fts MATCH :ftsQuery)
        ORDER BY n.updatedAtMillis DESC
    """)
    fun searchActiveNoteCardsByTopicTree(topicId: Long, origin: String, ftsQuery: String): Flow<List<NoteCardProjection>>

    @Query("SELECT * FROM topics WHERE origin = :origin ORDER BY name COLLATE NOCASE ASC")
    fun observeTopicsForOrigin(origin: String): Flow<List<com.clipnest.data.model.Topic>>

    @Query("UPDATE notes SET title = :title, content = :content, preview = SUBSTR(:content, 1, 320), normalizedTitle = :normalizedTitle, normalizedContent = :normalizedContent, updatedAtMillis = :now, editSessionCount = editSessionCount + 1, lastAuthoredAtMillis = :now WHERE id = :id")
    suspend fun updateContentAndBumpEditSession(
        id: Long,
        title: String,
        content: String,
        now: Long,
        normalizedTitle: String = SearchTextNormalizer.normalize(title),
        normalizedContent: String = SearchTextNormalizer.normalize(content)
    )

    @Query("UPDATE notes SET isPinned = :isPinned, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun setNotePinned(id: Long, isPinned: Boolean, updatedAtMillis: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM notes WHERE id = :id AND isPinned = 1)")
    suspend fun isNotePinned(id: Long): Boolean

    @Query("SELECT id FROM notes WHERE isPinned = 1 AND isDeleted = 0 AND isArchived = 0")
    fun observePinnedNoteIds(): Flow<List<Long>>

    @Query("UPDATE notes SET isDeleted = :isDeleted, deletedAtMillis = :deletedAtMillis, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun setDeleted(id: Long, isDeleted: Boolean, deletedAtMillis: Long?, updatedAtMillis: Long)

    @Query("UPDATE notes SET isArchived = :isArchived, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun setArchived(id: Long, isArchived: Boolean, updatedAtMillis: Long)

    @Query("UPDATE note_topic_cross_ref SET isPinned = :isPinned WHERE noteId = :noteId AND topicId = :topicId AND role = :role")
    suspend fun setTopicPinned(noteId: Long, topicId: Long, role: com.clipnest.data.model.NoteTopicRole, isPinned: Boolean,)

    @Query("SELECT EXISTS(SELECT 1 FROM note_topic_cross_ref WHERE noteId = :noteId AND topicId = :topicId AND role = :role AND isPinned = 1)")
    suspend fun isTopicPinned(noteId: Long, topicId: Long, role: com.clipnest.data.model.NoteTopicRole): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM note_topic_cross_ref WHERE noteId = :noteId AND topicId = :topicId AND role = :role)")
    suspend fun hasTopicRelation(noteId: Long, topicId: Long, role: com.clipnest.data.model.NoteTopicRole): Boolean

    @Query("SELECT DISTINCT topicId FROM note_topic_cross_ref WHERE noteId IN (:noteIds) AND role = :role")
    suspend fun getUserTopicIdsForNotes(noteIds: List<Long>, role: com.clipnest.data.model.NoteTopicRole): List<Long>

    @Query("SELECT r.noteId FROM note_topic_cross_ref r WHERE r.topicId = :topicId AND r.role = :role AND r.isPinned = 1")
    fun observePinnedNoteIdsForTopic(topicId: Long, role: com.clipnest.data.model.NoteTopicRole): Flow<List<Long>>

    @Query("UPDATE note_topic_cross_ref SET isPinned = :isPinned WHERE topicId = :topicId AND role = :role AND noteId IN (:noteIds)")
    suspend fun setTopicPinnedForNotes(noteIds: List<Long>, topicId: Long, role: com.clipnest.data.model.NoteTopicRole, isPinned: Boolean)

    @Query("""
        SELECT n.id, n.title, n.preview AS preview, n.updatedAtMillis, '' AS topicLabels
        FROM notes n
        WHERE n.isDeleted = 1
        ORDER BY n.deletedAtMillis DESC
    """)
    fun observeDeletedNotes(): Flow<List<NoteCardProjection>>

    @Query("""
        SELECT n.id, n.title, n.preview AS preview, n.updatedAtMillis, '' AS topicLabels
        FROM notes n
        WHERE n.isArchived = 1 AND n.isDeleted = 0
        ORDER BY n.updatedAtMillis DESC
    """)
    fun observeArchivedNotes(): Flow<List<NoteCardProjection>>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNotePermanently(id: Long)
}
