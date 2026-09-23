package com.clipnest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.clipnest.data.model.NoteTopicCrossRef
import com.clipnest.data.model.Topic
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTopic(topic: Topic): Long

    @Query(
        """
        SELECT * FROM topics
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    fun searchTopics(query: String): Flow<List<Topic>>

    @Query("SELECT * FROM topics ORDER BY name COLLATE NOCASE ASC")
    fun observeAllTopics(): Flow<List<Topic>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addNoteTopicCrossRef(crossRef: NoteTopicCrossRef)

    @Query(
        """
        DELETE FROM note_topic_cross_ref
        WHERE noteId = :noteId AND topicId = :topicId AND role = :role
        """
    )
    suspend fun removeNoteTopicCrossRef(
        noteId: Long,
        topicId: Long,
        role: com.clipnest.data.model.NoteTopicRole
    )

    @Query(
        """
        SELECT t.* FROM topics t
        INNER JOIN note_topic_cross_ref r ON r.topicId = t.id
        WHERE r.noteId = :noteId
        ORDER BY COALESCE(r.rank, 2147483647), t.name COLLATE NOCASE ASC
        """
    )
    fun observeTopicsForNote(noteId: Long): Flow<List<Topic>>

    @Query("SELECT * FROM topics WHERE id = :id LIMIT 1")
    suspend fun getTopicById(id: Long): Topic?

    @Query("SELECT * FROM topics WHERE name = :name LIMIT 1")
    suspend fun getTopicByName(name: String): Topic?
}
