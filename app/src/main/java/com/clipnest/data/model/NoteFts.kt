package com.clipnest.data.model

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Fts4(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=2"],
    contentEntity = Note::class,
    prefix = [2, 3, 4, 5, 6]
)
@Entity(tableName = "notes_fts")
data class NoteFts(
    val title: String,
    val content: String
)
