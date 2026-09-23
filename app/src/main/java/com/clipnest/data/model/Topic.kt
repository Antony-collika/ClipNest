package com.clipnest.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TopicOrigin {
    USER
}

enum class TopicLevel {
    PARENT,
    CHILD
}

@Entity(
    tableName = "topics",
    foreignKeys = [
        ForeignKey(
            entity = Topic::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("parentId")]
)
data class Topic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val parentId: Long? = null,
    val origin: TopicOrigin = TopicOrigin.USER,
    val level: TopicLevel = TopicLevel.PARENT,
    val icon: String? = null,
    val isPinned: Boolean = false,
    val createdAtMillis: Long
)
