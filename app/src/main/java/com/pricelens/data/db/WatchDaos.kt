package com.pricelens.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchDao {

    @Query(
        """
        SELECT w.id AS watchId, w.skuId AS skuId, k.spuId AS spuId, s.title AS spuTitle,
               k.displayName AS skuName, w.targetPriceCents AS targetPriceCents, w.enabled AS enabled,
               w.lastCheckedAt AS lastCheckedAt,
               (SELECT MIN(q.priceCents) FROM quotes q
                   WHERE q.skuId = k.id AND q.inStock = 1) AS lastPriceCents,
               (SELECT COUNT(*) FROM quotes q2 WHERE q2.skuId = k.id) AS quoteCount
        FROM watches w
        JOIN skus k ON w.skuId = k.id
        JOIN spus s ON k.spuId = s.id
        ORDER BY w.createdAt DESC
        """
    )
    fun observeRows(): Flow<List<WatchRow>>

    @Query("SELECT * FROM watches WHERE id = :watchId")
    suspend fun get(watchId: Long): WatchEntity?

    @Query("SELECT * FROM watches WHERE skuId = :skuId LIMIT 1")
    suspend fun findBySku(skuId: Long): WatchEntity?

    @Query("SELECT * FROM watches WHERE enabled = 1")
    suspend fun allEnabled(): List<WatchEntity>

    @Query("SELECT COUNT(*) FROM watches")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(watch: WatchEntity): Long

    @Update
    suspend fun update(watch: WatchEntity)

    @Delete
    suspend fun delete(watch: WatchEntity)
}

@Dao
interface AlertDao {

    @Query(
        """
        SELECT a.id AS alertId, a.watchId AS watchId, s.title AS spuTitle, k.displayName AS skuName,
               a.oldPriceCents AS oldPriceCents, a.newPriceCents AS newPriceCents,
               a.platform AS platform, a.shopName AS shopName, a.createdAt AS createdAt, a.read AS read
        FROM alerts a
        JOIN watches w ON a.watchId = w.id
        JOIN skus k ON w.skuId = k.id
        JOIN spus s ON k.spuId = s.id
        ORDER BY a.createdAt DESC
        LIMIT 200
        """
    )
    fun observeRows(): Flow<List<AlertRow>>

    @Query("SELECT COUNT(*) FROM alerts WHERE read = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("SELECT * FROM alerts WHERE watchId = :watchId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestForWatch(watchId: Long): AlertEntity?

    @Insert
    suspend fun insert(alert: AlertEntity): Long

    @Query("UPDATE alerts SET read = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM alerts")
    suspend fun clear()
}
