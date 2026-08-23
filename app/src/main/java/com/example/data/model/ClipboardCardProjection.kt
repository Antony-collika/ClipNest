package com.example.data.model

import androidx.room.ColumnInfo

data class ClipboardCardProjection(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "preview") val preview: String,
    @ColumnInfo(name = "createdAtMillis") val createdAtMillis: Long,
    @ColumnInfo(name = "sortOrder") val sortOrder: Long,
    @ColumnInfo(name = "sourceApp") val sourceApp: String?,
    @ColumnInfo(name = "contentType") val contentType: ContentType,
    @ColumnInfo(name = "pinned") val pinned: Boolean,
    @ColumnInfo(name = "isSensitive") val isSensitive: Boolean
)
