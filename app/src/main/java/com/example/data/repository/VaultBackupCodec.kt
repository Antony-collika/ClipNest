package com.example.data.repository

import com.example.data.model.ClipboardCard
import com.example.data.model.VaultBackupCard
import com.example.data.model.VaultBackupFile
import com.example.data.model.VAULT_BACKUP_FORMAT
import com.example.data.model.VAULT_BACKUP_VERSION
import com.squareup.moshi.Moshi

object VaultBackupCodec {
    private val adapter = Moshi.Builder()
        .build()
        .adapter(VaultBackupFile::class.java)
        .indent("  ")

    fun encode(cards: List<ClipboardCard>): String {
        val backup = VaultBackupFile(
            cards = cards.map { card ->
                VaultBackupCard(
                    content = card.content,
                    createdAtMillis = card.createdAtMillis,
                    pinned = card.pinned,
                    isSensitive = card.isSensitive
                )
            }
        )
        return adapter.toJson(backup)
    }

    fun decode(json: String): VaultBackupFile {
        val backup = adapter.fromJson(json)
            ?: throw IllegalArgumentException("Backup file is empty")
        require(backup.format == VAULT_BACKUP_FORMAT) { "Unsupported backup format" }
        require(backup.version in 1..VAULT_BACKUP_VERSION) { "Unsupported backup version" }
        return backup
    }
}
