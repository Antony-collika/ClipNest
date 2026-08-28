package com.clipnest.data.model

import com.squareup.moshi.JsonClass

const val VAULT_BACKUP_FORMAT = "clipnest-backup"
const val VAULT_BACKUP_VERSION = 1
const val ENCRYPTED_BACKUP_FORMAT = "clipnest-encrypted-backup"
const val ENCRYPTED_BACKUP_VERSION = 1

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
