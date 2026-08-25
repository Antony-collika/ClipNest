package com.example.data.model

import com.squareup.moshi.JsonClass

const val VAULT_BACKUP_FORMAT = "xboard-backup"
const val VAULT_BACKUP_VERSION = 1

@JsonClass(generateAdapter = true)
data class VaultBackupFile(
    val format: String = VAULT_BACKUP_FORMAT,
    val version: Int = VAULT_BACKUP_VERSION,
    val cards: List<VaultBackupCard> = emptyList()
)

@JsonClass(generateAdapter = true)
data class VaultBackupCard(
    val content: String,
    val createdAtMillis: Long,
    val pinned: Boolean = false,
    val isSensitive: Boolean = false
)

data class VaultBackupResult(
    val imported: Int,
    val skippedDuplicates: Int,
    val skippedInvalid: Int
)
