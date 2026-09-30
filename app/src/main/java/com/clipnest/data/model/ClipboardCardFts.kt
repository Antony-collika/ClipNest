package com.clipnest.data.model

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Fts4(
    tokenizer = FtsOptions.TOKENIZER_SIMPLE,
    contentEntity = ClipboardCard::class,
)
@Entity(tableName = "clipboard_cards_fts")
data class ClipboardCardFts(
    val normalizedContent: String,
    val normalizedSourceApp: String
)
