package com.clipnest.data.repository

import com.clipnest.data.local.ClipboardDao
import com.clipnest.data.local.RetentionPolicy
import com.clipnest.data.model.ClipboardCard
import com.clipnest.data.model.ClipboardCardProjection
import com.clipnest.data.model.VaultBackupCard
import com.clipnest.data.model.VaultBackupResult
import com.clipnest.data.model.ContentType
import com.clipnest.domain.OrderHelper
import com.clipnest.domain.FtsSearchQuery
import com.clipnest.domain.TextNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

/** A single normalized capture request used by every capture entry point. */
data class CapturePayload(
    val content: String,
    val sourceApp: String?,
    val contentType: ContentType? = null,
    val isSensitive: Boolean = false,
    val pinned: Boolean = false
)

interface ClipboardRepository {
    fun getAllCardProjections(): Flow<List<ClipboardCardProjection>>
    fun searchCardProjections(query: String): Flow<List<ClipboardCardProjection>>
    suspend fun getAllCards(): List<ClipboardCard>
    suspend fun mergeBackupCards(cards: List<VaultBackupCard>): VaultBackupResult
    suspend fun getCardById(id: Long): ClipboardCard?
    suspend fun getCardsByIds(ids: List<Long>): List<ClipboardCard>
    suspend fun forEachCardsByIds(ids: List<Long>, action: suspend (List<ClipboardCard>) -> Unit)
    suspend fun saveCard(
        content: String,
        sourceApp: String? = null,
        contentType: ContentType? = null,
        isSensitive: Boolean = false,
        pinned: Boolean = false
    ): ClipboardCard?
    suspend fun saveCards(payloads: List<CapturePayload>): List<ClipboardCard>
    suspend fun deleteCards(ids: List<Long>)
    suspend fun setPinned(ids: List<Long>, pinned: Boolean)
    suspend fun setSensitive(id: Long, isSensitive: Boolean)
    suspend fun updateSortOrders(idToOrderList: List<Pair<Long, Long>>)
    suspend fun cleanupOldCards(policy: RetentionPolicy): Int
}

class ClipboardRepositoryImpl(
    private val dao: ClipboardDao
) : ClipboardRepository {

    private companion object {
        const val CONTENT_BATCH_SIZE = 50
    }

    override fun getAllCardProjections(): Flow<List<ClipboardCardProjection>> {
        return dao.getAllCardProjections()
    }

    override suspend fun getAllCards(): List<ClipboardCard> {
        val ids = dao.getAllCardProjections().first().map { it.id }
        return ids.chunked(CONTENT_BATCH_SIZE).flatMap { batch -> dao.getCardsByIds(batch) }
    }

    override suspend fun mergeBackupCards(cards: List<VaultBackupCard>): VaultBackupResult {
        if (cards.isEmpty()) {
            return VaultBackupResult(imported = 0, skippedDuplicates = 0, skippedInvalid = 0)
        }

        val existingKeys = mutableSetOf<BackupCardKey>()
        val existingIds = dao.getAllCardProjections().first().map { it.id }
        existingIds.chunked(CONTENT_BATCH_SIZE).forEach { batch ->
            dao.getCardsByIds(batch).forEach { card ->
                existingKeys += BackupCardKey(contentHash(card.content), card.createdAtMillis)
            }
        }
        val importedCards = mutableListOf<ClipboardCard>()
        var skippedDuplicates = 0
        var skippedInvalid = 0

        cards.forEach { backupCard ->
            if (backupCard.content.isBlank()) {
                skippedInvalid++
                return@forEach
            }
            val key = BackupCardKey(contentHash(backupCard.content), backupCard.createdAtMillis)
            if (!existingKeys.add(key)) {
                skippedDuplicates++
                return@forEach
            }
            importedCards += ClipboardCard(
                id = 0L,
                content = backupCard.content,
                createdAtMillis = backupCard.createdAtMillis,
                sortOrder = 0L,
                sourceApp = null,
                contentType = TextNormalizer.detectContentType(backupCard.content),
                pinned = backupCard.pinned,
                preview = TextNormalizer.generatePreview(backupCard.content),
                isSensitive = backupCard.isSensitive
            )
        }

        if (importedCards.isEmpty()) {
            return VaultBackupResult(
                imported = 0,
                skippedDuplicates = skippedDuplicates,
                skippedInvalid = skippedInvalid
            )
        }

        var nextId = dao.getMaxId()
        val now = System.currentTimeMillis()
        val storedCards = importedCards.mapIndexed { index, card ->
            nextId = maxOf(nextId + 1L, now + index)
            card.copy(
                id = nextId,
                sortOrder = card.createdAtMillis + index
            )
        }
        dao.insertCards(storedCards)
        return VaultBackupResult(
            imported = storedCards.size,
            skippedDuplicates = skippedDuplicates,
            skippedInvalid = skippedInvalid
        )
    }

    override fun searchCardProjections(query: String): Flow<List<ClipboardCardProjection>> {
        val ftsQuery = FtsSearchQuery.fromUserQuery(query)
        return if (ftsQuery.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else dao.searchCardProjections(ftsQuery)
    }

    override suspend fun getCardById(id: Long): ClipboardCard? {
        return dao.getCardById(id)
    }

    override suspend fun getCardsByIds(ids: List<Long>): List<ClipboardCard> {
        if (ids.isEmpty()) return emptyList()
        val result = ArrayList<ClipboardCard>(ids.size)
        for (batch in ids.chunked(CONTENT_BATCH_SIZE)) {
            val cards = dao.getCardsByIds(batch)
            val map = cards.associateBy { it.id }
            batch.forEach { id -> map[id]?.let(result::add) }
        }
        return result
    }

    override suspend fun forEachCardsByIds(
        ids: List<Long>,
        action: suspend (List<ClipboardCard>) -> Unit
    ) {
        if (ids.isEmpty()) return
        for (batch in ids.chunked(CONTENT_BATCH_SIZE)) {
            val cards = dao.getCardsByIds(batch)
            val map = cards.associateBy { it.id }
            val ordered = batch.mapNotNull { map[it] }
            if (ordered.isNotEmpty()) action(ordered)
        }
    }

    override suspend fun saveCard(
        content: String,
        sourceApp: String?,
        contentType: ContentType?,
        isSensitive: Boolean,
        pinned: Boolean
    ): ClipboardCard? {
        return saveCards(
            listOf(
                CapturePayload(
                    content = content,
                    sourceApp = sourceApp,
                    contentType = contentType,
                    isSensitive = isSensitive,
                    pinned = pinned
                )
            )
        ).firstOrNull()
    }

    override suspend fun saveCards(payloads: List<CapturePayload>): List<ClipboardCard> {
        val normalizedPayloads = payloads.mapNotNull { payload ->
            val normalized = TextNormalizer.normalize(payload.content) ?: return@mapNotNull null
            val source = payload.sourceApp?.trim()?.ifBlank { null }
            val resolvedType = payload.contentType ?: TextNormalizer.detectContentType(normalized)
            Triple(payload, normalized, resolvedType)
        }

        if (normalizedPayloads.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val cards = normalizedPayloads.mapIndexed { index, (payload, normalized, resolvedType) ->
            ClipboardCard(
                id = 0L,
                content = normalized,
                createdAtMillis = now + index,
                sortOrder = 0L,
                sourceApp = payload.sourceApp?.trim()?.ifBlank { null },
                contentType = resolvedType,
                pinned = payload.pinned,
                preview = TextNormalizer.generatePreview(normalized),
                isSensitive = payload.isSensitive
            )
        }

        return dao.insertCardsAtEnd(cards)
    }

    override suspend fun deleteCards(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.deleteCardsByIds(ids)
    }

    override suspend fun setPinned(ids: List<Long>, pinned: Boolean) {
        if (ids.isNotEmpty()) dao.setPinnedForIds(ids, pinned)
    }

    override suspend fun setSensitive(id: Long, isSensitive: Boolean) {
        dao.setSensitiveForId(id, isSensitive)
    }

    override suspend fun updateSortOrders(idToOrderList: List<Pair<Long, Long>>) {
        if (idToOrderList.isNotEmpty()) dao.updateSortOrders(idToOrderList)
    }

    override suspend fun cleanupOldCards(policy: RetentionPolicy): Int {
        if (policy == RetentionPolicy.NEVER || policy.days <= 0) return 0
        val cutoff = System.currentTimeMillis() - (policy.days * 24L * 60L * 60L * 1000L)
        return dao.deleteOlderThan(cutoff)
    }
}


data class BackupCardKey(
    val contentHash: String,
    val createdAtMillis: Long
)

private fun contentHash(content: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(content.toByteArray(Charsets.UTF_8)).joinToString("") { byte -> "%02x".format(byte) }
}
