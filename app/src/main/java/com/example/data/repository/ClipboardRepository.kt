package com.example.data.repository

import com.example.data.local.ClipboardDao
import com.example.data.local.RetentionPolicy
import com.example.data.model.ClipboardCard
import com.example.data.model.ClipboardCardProjection
import com.example.data.model.ContentType
import com.example.domain.OrderHelper
import com.example.domain.TextNormalizer
import kotlinx.coroutines.flow.Flow

interface ClipboardRepository {
    fun getAllCardProjections(): Flow<List<ClipboardCardProjection>>
    fun searchCardProjections(query: String): Flow<List<ClipboardCardProjection>>
    suspend fun getCardById(id: Long): ClipboardCard?
    suspend fun getCardsByIds(ids: List<Long>): List<ClipboardCard>
    suspend fun saveCard(
        content: String,
        sourceApp: String? = null,
        contentType: ContentType? = null,
        isSensitive: Boolean = false,
        pinned: Boolean = false
    ): ClipboardCard?
    suspend fun deleteCards(ids: List<Long>)
    suspend fun setPinned(ids: List<Long>, pinned: Boolean)
    suspend fun setSensitive(id: Long, isSensitive: Boolean)
    suspend fun updateSortOrders(idToOrderList: List<Pair<Long, Long>>)
    suspend fun cleanupOldCards(policy: RetentionPolicy): Int
}

class ClipboardRepositoryImpl(
    private val dao: ClipboardDao
) : ClipboardRepository {

    override fun getAllCardProjections(): Flow<List<ClipboardCardProjection>> {
        return dao.getAllCardProjections()
    }

    override fun searchCardProjections(query: String): Flow<List<ClipboardCardProjection>> {
        return dao.searchCardProjections(query)
    }

    override suspend fun getCardById(id: Long): ClipboardCard? {
        return dao.getCardById(id)
    }

    override suspend fun getCardsByIds(ids: List<Long>): List<ClipboardCard> {
        val cards = dao.getCardsByIds(ids)
        // Preserve input ID order
        val map = cards.associateBy { it.id }
        return ids.mapNotNull { map[it] }
    }

    override suspend fun saveCard(
        content: String,
        sourceApp: String?,
        contentType: ContentType?,
        isSensitive: Boolean,
        pinned: Boolean
    ): ClipboardCard? {
        val normalized = TextNormalizer.normalize(content) ?: return null
        val preview = TextNormalizer.generatePreview(normalized)
        val resolvedType = contentType ?: TextNormalizer.detectContentType(normalized)
        val now = System.currentTimeMillis()
        val maxOrder = dao.getMaxSortOrder()
        val nextOrder = maxOrder + OrderHelper.ORDER_STEP

        val card = ClipboardCard(
            id = now + (0..999).random(),
            content = normalized,
            createdAtMillis = now,
            sortOrder = nextOrder,
            sourceApp = sourceApp?.ifBlank { null },
            contentType = resolvedType,
            pinned = pinned,
            preview = preview,
            isSensitive = isSensitive
        )

        dao.insertCard(card)
        return card
    }

    override suspend fun deleteCards(ids: List<Long>) {
        if (ids.isNotEmpty()) {
            dao.deleteCardsByIds(ids)
        }
    }

    override suspend fun setPinned(ids: List<Long>, pinned: Boolean) {
        if (ids.isNotEmpty()) {
            dao.setPinnedForIds(ids, pinned)
        }
    }

    override suspend fun setSensitive(id: Long, isSensitive: Boolean) {
        dao.setSensitiveForId(id, isSensitive)
    }

    override suspend fun updateSortOrders(idToOrderList: List<Pair<Long, Long>>) {
        if (idToOrderList.isNotEmpty()) {
            dao.updateSortOrders(idToOrderList)
        }
    }

    override suspend fun cleanupOldCards(policy: RetentionPolicy): Int {
        if (policy == RetentionPolicy.NEVER || policy.days <= 0) return 0
        val cutoff = System.currentTimeMillis() - (policy.days * 24L * 60L * 60L * 1000L)
        return dao.deleteOlderThan(cutoff)
    }
}
