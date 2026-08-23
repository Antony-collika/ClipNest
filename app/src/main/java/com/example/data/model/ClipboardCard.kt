package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clipboard_cards")
data class ClipboardCard(
    @PrimaryKey val id: Long,
    val content: String,
    val createdAtMillis: Long,
    val sortOrder: Long,
    val sourceApp: String?,
    val contentType: ContentType,
    val pinned: Boolean,
    val preview: String,
    val isSensitive: Boolean
)
