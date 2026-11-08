package com.pricelens.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SpuDao {

    @Query(
        """
        SELECT s.id AS spuId, s.title AS title, s.category AS category,
          (SELECT COUNT(*) FROM skus k WHERE k.spuId = s.id) AS skuCount,
          (SELECT COUNT(*) FROM quotes q JOIN skus k2 ON q.skuId = k2.id WHERE k2.spuId = s.id) AS quoteCount,
          (SELECT MIN(q3.priceCents) FROM quotes q3 JOIN skus k3 ON q3.skuId = k3.id
              WHERE k3.spuId = s.id AND q3.inStock = 1) AS minPriceCents,
          (SELECT MAX(q4.priceCents) FROM quotes q4 JOIN skus k4 ON q4.skuId = k4.id
              WHERE k4.spuId = s.id AND q4.inStock = 1) AS maxPriceCents,
          s.updatedAt AS lastCapturedAt
        FROM spus s
        ORDER BY s.updatedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<SpuSummaryRow>>

    @Query("SELECT * FROM spus WHERE id = :spuId")
    suspend fun getSpu(spuId: Long): SpuEntity?

    @Query("SELECT * FROM spus WHERE id = :spuId")
    fun observeSpu(spuId: Long): Flow<SpuEntity?>

    @Query("SELECT * FROM spus ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recentSpus(limit: Int): List<SpuEntity>

    @Query("SELECT * FROM spus WHERE normalizedTitle = :normalizedTitle LIMIT 5")
    suspend fun findByNormalizedTitle(normalizedTitle: String): List<SpuEntity>

    @Query("SELECT COUNT(*) FROM spus")
    suspend fun count(): Int

    @Insert
    suspend fun insert(spu: SpuEntity): Long

    @Update
    suspend fun update(spu: SpuEntity)

    @Query("UPDATE spus SET updatedAt = :timestamp WHERE id IN (:ids)")
    suspend fun touch(ids: List<Long>, timestamp: Long)
}

@Dao
interface SkuDao {

    @Query("SELECT * FROM skus WHERE spuId = :spuId ORDER BY id")
    suspend fun skusOf(spuId: Long): List<SkuEntity>

    @Query("SELECT * FROM skus WHERE spuId = :spuId ORDER BY id")
    fun observeSkusOf(spuId: Long): Flow<List<SkuEntity>>

    @Query(
        """
        SELECT k.id AS skuId, k.spuId AS spuId, k.displayName AS displayName, k.normSpec AS normSpec,
          (SELECT COUNT(*) FROM quotes q WHERE q.skuId = k.id) AS quoteCount,
          (SELECT MIN(q2.priceCents) FROM quotes q2 WHERE q2.skuId = k.id AND q2.inStock = 1) AS minPriceCents,
          (SELECT MAX(q3.priceCents) FROM quotes q3 WHERE q3.skuId = k.id AND q3.inStock = 1) AS maxPriceCents
        FROM skus k WHERE k.spuId = :spuId ORDER BY k.id
        """
    )
    fun observeSkuStats(spuId: Long): Flow<List<SkuStatsRow>>

    @Query("SELECT * FROM skus WHERE spuId = :spuId AND normSpec = :normSpec LIMIT 1")
    suspend fun findBySpec(spuId: Long, normSpec: String): SkuEntity?

    @Query("SELECT * FROM skus WHERE id = :skuId")
    suspend fun getSku(skuId: Long): SkuEntity?

    @Query("SELECT * FROM skus WHERE id = :skuId")
    fun observeSku(skuId: Long): Flow<SkuEntity?>

    @Insert
    suspend fun insert(sku: SkuEntity): Long
}

@Dao
interface QuoteDao {

    @Insert
    suspend fun insert(quote: QuoteEntity): Long

    @Query(
        """
        SELECT * FROM quotes WHERE skuId = :skuId
        ORDER BY inStock DESC, priceCents ASC, capturedAt DESC
        """
    )
    fun observeQuotesOfSku(skuId: Long): Flow<List<QuoteEntity>>

    @Query(
        """
        SELECT * FROM quotes WHERE skuId = :skuId
        ORDER BY inStock DESC, priceCents ASC, capturedAt DESC
        """
    )
    suspend fun quotesOfSku(skuId: Long): List<QuoteEntity>

    @Query("SELECT * FROM quotes WHERE offerKey = :offerKey ORDER BY capturedAt ASC")
    suspend fun historyOfOffer(offerKey: String): List<QuoteEntity>

    @Query("SELECT * FROM quotes WHERE offerKey = :offerKey ORDER BY capturedAt DESC LIMIT 1")
    suspend fun latestOfOffer(offerKey: String): QuoteEntity?

    @Query("SELECT * FROM quotes WHERE skuId = :skuId ORDER BY capturedAt ASC")
    suspend fun trendsOfSku(skuId: Long): List<QuoteEntity>

    @Query("SELECT * FROM quotes WHERE skuId = :skuId AND capturedAt >= :since ORDER BY capturedAt DESC")
    suspend fun quotesSince(skuId: Long, since: Long): List<QuoteEntity>

    @Query(
        """
        SELECT * FROM quotes
        WHERE skuId = :skuId AND inStock = 1 AND capturedAt BETWEEN :from AND :to
        ORDER BY priceCents ASC LIMIT 1
        """
    )
    suspend fun cheapestQuoteForSkuBetween(skuId: Long, from: Long, to: Long): QuoteEntity?

    @Query(
        """
        SELECT q.* FROM quotes q JOIN skus k ON q.skuId = k.id
        WHERE k.spuId = :spuId ORDER BY q.capturedAt DESC
        """
    )
    suspend fun quotesOfSpu(spuId: Long): List<QuoteEntity>

    @Query(
        """
        SELECT q.* FROM quotes q JOIN skus k ON q.skuId = k.id
        WHERE k.spuId = :spuId ORDER BY q.capturedAt DESC
        """
    )
    fun observeQuotesOfSpu(spuId: Long): Flow<List<QuoteEntity>>

    @Query(
        """
        SELECT MIN(priceCents) FROM quotes
        WHERE skuId = :skuId AND inStock = 1 AND capturedAt < :timestamp
        """
    )
    suspend fun minInStockPriceBefore(skuId: Long, timestamp: Long): Long?

    @Query(
        """
        SELECT MIN(priceCents) FROM quotes
        WHERE skuId = :skuId AND inStock = 1 AND capturedAt >= :from AND capturedAt <= :to
        """
    )
    suspend fun minInStockPriceBetween(skuId: Long, from: Long, to: Long): Long?

    @Query(
        """
        SELECT q.id FROM quotes q
        JOIN skus k ON q.skuId = k.id
        WHERE k.spuId = :spuId AND q.inStock = 1 AND q.capturedAt BETWEEN :from AND :to
        ORDER BY q.priceCents ASC LIMIT 1
        """
    )
    suspend fun cheapestQuoteBetween(spuId: Long, from: Long, to: Long): Long?

    @Query(
        """
        SELECT k.spuId AS spuId, q.platform AS platform, q.shopName AS shopName,
               q.link AS link, MIN(q.priceCents) AS priceCents
        FROM quotes q JOIN skus k ON q.skuId = k.id
        WHERE q.inStock = 1
        GROUP BY k.spuId
        """
    )
    fun observeCheapestPerSpu(): Flow<List<CheapestOfferRow>>

    @Query(
        """
        SELECT s.title AS spuTitle, s.category AS category, k.displayName AS skuName,
               q.platform AS platform, q.productTitle AS productTitle, q.priceCents AS priceCents,
               q.listPriceCents AS listPriceCents, q.salesCount AS salesCount, q.shopName AS shopName,
               q.shopRating AS shopRating, q.inStock AS inStock, q.link AS link,
               q.sourceKind AS sourceKind, q.capturedAt AS capturedAt, q.offerKey AS offerKey
        FROM quotes q
        JOIN skus k ON q.skuId = k.id
        JOIN spus s ON k.spuId = s.id
        ORDER BY s.title ASC, k.displayName ASC, q.capturedAt ASC
        """
    )
    suspend fun exportRows(): List<ExportQuoteRow>

    @Query("SELECT COUNT(*) FROM quotes")
    fun observeQuoteCount(): Flow<Int>
}
