package com.clipnest.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

enum class NoteTopicRole {
    USER_TAG
}

@Entity(
    tableName = "note_topic_cross_ref",
    primaryKeys = ["noteId", "topicId", "role"],
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Topic::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId"), Index("topicId")]
)
data class NoteTopicCrossRef(
    val noteId: Long,
    val topicId: Long,
    val role: NoteTopicRole = NoteTopicRole.USER_TAG,
    val rank: Int? = null
)
