package com.clipnest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.clipnest.data.model.NoteTopicCrossRef
import com.clipnest.data.model.Topic
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTopicInternal(topic: Topic): Long

    suspend fun insertTopic(topic: Topic): Long {
        val parentId = topic.parentId
        if (parentId != null) {
            val parent = getTopicById(parentId)
                ?: throw IllegalArgumentException("Parent topic does not exist")
            require(parent.origin == topic.origin) {
                "Parent and child topics must have the same origin"
            }
            require(parent.parentId == null) {
                "A child topic cannot itself be a parent"
            }
        }
        return insertTopicInternal(topic)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM topics WHERE parentId = :topicId)")
    suspend fun hasChildren(topicId: Long): Boolean

    @Query("UPDATE topics SET parentId = :parentId WHERE id = :topicId")
    suspend fun updateParentIdInternal(topicId: Long, parentId: Long?)

    @androidx.room.Transaction
    suspend fun makeTopicChild(topicId: Long, parentId: Long) {
        require(topicId != parentId) { "A topic cannot be its own parent" }
        val topic = getTopicById(topicId)
            ?: throw IllegalArgumentException("Topic does not exist")
        require(topic.parentId == null) { "Only a free topic can become a child" }
        require(!hasChildren(topicId)) { "A topic with children cannot become a child" }

        val parent = getTopicById(parentId)
            ?: throw IllegalArgumentException("Parent topic does not exist")
        require(parent.parentId == null) { "Parent must be a root topic" }
        require(parent.origin == topic.origin) {
            "Parent and child topics must have the same origin"
        }
        updateParentIdInternal(topicId, parentId)
    }

    @androidx.room.Transaction
    suspend fun makeTopicFree(topicId: Long) {
        val topic = getTopicById(topicId)
            ?: throw IllegalArgumentException("Topic does not exist")
        require(topic.parentId != null) { "Topic is already free" }
        require(!hasChildren(topicId)) { "A child topic cannot have children" }
        updateParentIdInternal(topicId, null)
    }

    @Query("SELECT * FROM topics WHERE origin = :origin AND name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE ASC")
    fun searchTopics(query: String, origin: String): Flow<List<Topic>>

    @Query("SELECT * FROM topics WHERE origin = 'USER' AND name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE ASC")
    fun searchTopics(query: String): Flow<List<Topic>>

    @Query("SELECT * FROM topics WHERE origin = :origin ORDER BY name COLLATE NOCASE ASC")
    fun observeAllTopics(origin: String): Flow<List<Topic>>

    @Query("SELECT * FROM topics WHERE origin = :origin AND parentId IS NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeRootTopics(origin: String): Flow<List<Topic>>

    @Query("SELECT * FROM topics WHERE origin = :origin AND parentId = :parentId ORDER BY name COLLATE NOCASE ASC")
    fun observeChildren(parentId: Long, origin: String): Flow<List<Topic>>

    @Transaction
    @Query("SELECT * FROM topics WHERE origin = :origin AND parentId IS NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeTopicTree(origin: String): Flow<List<com.clipnest.data.model.TopicTreeNode>>

    @Query(
        """
        SELECT
            t.*,
            CASE WHEN EXISTS(
                SELECT 1 FROM topics child
                WHERE child.parentId = t.id
            ) THEN 1 ELSE 0 END AS isParent,
            CASE WHEN t.parentId IS NOT NULL THEN 1 ELSE 0 END AS isChild,
            CASE WHEN t.parentId IS NULL AND NOT EXISTS(
                SELECT 1 FROM topics child
                WHERE child.parentId = t.id
            ) THEN 1 ELSE 0 END AS isFree
        FROM topics t
        WHERE t.origin = :origin
        ORDER BY t.parentId IS NOT NULL, t.name COLLATE NOCASE ASC
        """
    )
    fun observeTopicHierarchy(origin: String): Flow<List<com.clipnest.data.model.TopicHierarchyState>>

    @Query("SELECT * FROM topics ORDER BY name COLLATE NOCASE ASC")
    fun observeAllTopics(): Flow<List<Topic>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addNoteTopicCrossRef(crossRef: NoteTopicCrossRef)

    @Query("DELETE FROM note_topic_cross_ref WHERE noteId = :noteId AND topicId = :topicId AND role = :role")
    suspend fun removeNoteTopicCrossRef(
        noteId: Long,
        topicId: Long,
        role: com.clipnest.data.model.NoteTopicRole
    )

    @Query("SELECT t.* FROM topics t INNER JOIN note_topic_cross_ref r ON r.topicId = t.id WHERE r.noteId = :noteId AND t.origin = :origin ORDER BY COALESCE(r.rank, 2147483647), t.name COLLATE NOCASE ASC")
    fun observeTopicsForNote(noteId: Long, origin: String): Flow<List<Topic>>

    @Query("SELECT t.* FROM topics t INNER JOIN note_topic_cross_ref r ON r.topicId = t.id WHERE r.noteId = :noteId ORDER BY COALESCE(r.rank, 2147483647), t.name COLLATE NOCASE ASC")
    fun observeTopicsForNote(noteId: Long): Flow<List<Topic>>

    @Query("SELECT * FROM topics WHERE id = :id LIMIT 1")
    suspend fun getTopicById(id: Long): Topic?

    @Query("SELECT * FROM topics WHERE name = :name AND origin = :origin LIMIT 1")
    suspend fun getTopicByName(name: String, origin: String): Topic?

    @Query("SELECT * FROM topics WHERE name = :name AND origin = 'USER' LIMIT 1")
    suspend fun getTopicByName(name: String): Topic?
}
