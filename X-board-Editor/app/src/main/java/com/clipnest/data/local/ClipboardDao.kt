package com.clipnest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.clipnest.data.model.ClipboardCard
import com.clipnest.data.model.ClipboardCardProjection
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {

    @Query(
        """
        SELECT id, preview, createdAtMillis, sortOrder, sourceApp, contentType, pinned, isSensitive 
        FROM clipboard_cards 
        ORDER BY sortOrder DESC
        """
    )
    fun getAllCardProjections(): Flow<List<ClipboardCardProjection>>

    @Query(
        """
        SELECT id, preview, createdAtMillis, sortOrder, sourceApp, contentType, pinned, isSensitive 
        FROM clipboard_cards 
        WHERE content LIKE '%' || :query || '%' OR (sourceApp IS NOT NULL AND sourceApp LIKE '%' || :query || '%')
        ORDER BY sortOrder DESC
        """
    )
    fun searchCardProjections(query: String): Flow<List<ClipboardCardProjection>>

    @Query("SELECT * FROM clipboard_cards ORDER BY sortOrder DESC")
    suspend fun getAllCards(): List<ClipboardCard>

    @Query("SELECT * FROM clipboard_cards WHERE id = :id")
    suspend fun getCardById(id: Long): ClipboardCard?

    @Query("SELECT * FROM clipboard_cards WHERE id IN (:ids)")
    suspend fun getCardsByIds(ids: List<Long>): List<ClipboardCard>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM clipboard_cards")
    suspend fun getMaxSortOrder(): Long

    @Query("SELECT COALESCE(MAX(id), 0) FROM clipboard_cards")
    suspend fun getMaxId(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: ClipboardCard): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<ClipboardCard>): List<Long>

    @Transaction
    suspend fun insertCardsAtEnd(cards: List<ClipboardCard>): List<ClipboardCard> {
        if (cards.isEmpty()) return emptyList()
        var currentOrder = getMaxSortOrder()
        var currentId = getMaxId()
        return cards.map { card ->
            val now = System.currentTimeMillis()
            val nextId = maxOf(currentId + 1L, now)
            val nextOrder = maxOf(currentOrder + 1000L, now)
            val storedCard = card.copy(id = nextId, sortOrder = nextOrder)
            insertCard(storedCard)
            currentId = nextId
            currentOrder = nextOrder
            storedCard
        }
    }

    @Update
    suspend fun updateCards(cards: List<ClipboardCard>)

    @Query("DELETE FROM clipboard_cards WHERE id IN (:ids)")
    suspend fun deleteCardsByIds(ids: List<Long>)

    @Query("UPDATE clipboard_cards SET pinned = :pinned WHERE id IN (:ids)")
    suspend fun setPinnedForIds(ids: List<Long>, pinned: Boolean)

    @Query("UPDATE clipboard_cards SET isSensitive = :isSensitive WHERE id = :id")
    suspend fun setSensitiveForId(id: Long, isSensitive: Boolean)

    @Query("UPDATE clipboard_cards SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Long)

    @Query("DELETE FROM clipboard_cards WHERE pinned = 0 AND createdAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Transaction
    suspend fun updateSortOrders(idToOrderList: List<Pair<Long, Long>>) {
        for ((id, sortOrder) in idToOrderList) {
            updateSortOrder(id, sortOrder)
        }
    }
}
