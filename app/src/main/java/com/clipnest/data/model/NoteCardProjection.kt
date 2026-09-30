package com.clipnest.data.model

data class NoteCardProjection(
    val id: Long,
    val title: String,
    val preview: String,
    val updatedAtMillis: Long,
    val topicLabels: String
)
