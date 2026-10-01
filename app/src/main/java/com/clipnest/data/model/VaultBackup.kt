package com.clipnest.data.model

import com.squareup.moshi.JsonClass

const val VAULT_BACKUP_FORMAT = "clipnest-backup"
const val VAULT_BACKUP_VERSION = 2
const val ENCRYPTED_BACKUP_FORMAT = "clipnest-encrypted-backup"
const val ENCRYPTED_BACKUP_VERSION = 1

@JsonClass(generateAdapter = true)
data class VaultBackupFile(
    val format: String = VAULT_BACKUP_FORMAT,
    val version: Int = VAULT_BACKUP_VERSION,
    val cards: List<VaultBackupCard> = emptyList(),
    val notes: List<VaultBackupNote> = emptyList()
)

@JsonClass(generateAdapter = true)
data class VaultBackupNote(
    val title: String,
    val content: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAtMillis: Long? = null,
    val editSessionCount: Long = 0L,
    val lastAuthoredAtMillis: Long? = null
)

@JsonClass(generateAdapter = true)
data class VaultBackupCard(
    val content: String,
    val createdAtMillis: Long,
    val pinned: Boolean = false,
    val isSensitive: Boolean = false
)

@JsonClass(generateAdapter = true)
data class EncryptedVaultBackup(
    val format: String = ENCRYPTED_BACKUP_FORMAT,
    val version: Int = ENCRYPTED_BACKUP_VERSION,
    val cipher: String = "AES-256-GCM",
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int,
    val salt: String,
    val nonce: String,
    val ciphertext: String
)

data class VaultBackupResult(
    val imported: Int,
    val skippedDuplicates: Int,
    val skippedInvalid: Int
)
