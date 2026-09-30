package com.clipnest.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.clipnest.domain.SearchTextNormalizer

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
    val isSensitive: Boolean,
    val normalizedContent: String = SearchTextNormalizer.normalize(content),
    val normalizedSourceApp: String = SearchTextNormalizer.normalize(sourceApp.orEmpty())
)
