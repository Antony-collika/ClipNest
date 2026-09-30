package com.clipnest.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Topic origins are strings so new classification sources can be added
 * without changing the database schema.
 */
object TopicOrigins {
    const val USER = "USER"
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
    indices = [
        Index("parentId"),
        Index(value = ["origin", "name"]),
        Index(value = ["origin", "parentId"])
    ]
)
data class Topic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val parentId: Long? = null,
    val origin: String = TopicOrigins.USER,
    val icon: String? = null,
    val isPinned: Boolean = false,
    val createdAtMillis: Long
)
