package com.clipnest.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAtMillis: Long? = null,
    val editSessionCount: Long = 0L,
    val lastAuthoredAtMillis: Long? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
