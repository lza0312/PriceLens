package com.pricelens.data.repo

import com.pricelens.collect.AdapterRegistry
import com.pricelens.collect.AdapterUnavailableException
import com.pricelens.core.Logger
import com.pricelens.data.db.AlertRow
import com.pricelens.data.db.AppDatabase
import com.pricelens.data.db.WatchEntity
import com.pricelens.data.db.WatchRow
import com.pricelens.data.prefs.SettingsStore
import com.pricelens.domain.model.OfferSnapshot
import com.pricelens.domain.model.Platform
import com.pricelens.domain.model.SourceKind
import com.pricelens.domain.service.SkuSpecParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn

data class WatchCheckReport(
    val watchCount: Int,
    val fetched: Int,
    val skipped: Int,
    val alerts: List<AlertEvent>,
    val notes: List<String>
)

class WatchRepository(
    private val db: AppDatabase,
    private val settingsStore: SettingsStore,
    private val registry: AdapterRegistry,
    private val priceRepository: PriceRepository
) {

    private val watchDao = db.watchDao()
    private val alertDao = db.alertDao()
    private val quoteDao = db.quoteDao()

    fun observeRows(): Flow<List<WatchRow>> = watchDao.observeRows()

    fun observeAlerts(): Flow<List<AlertRow>> = alertDao.observeRows()

    fun observeUnreadAlerts(): Flow<Int> = alertDao.observeUnreadCount()

    suspend fun findWatch(skuId: Long): WatchEntity? = watchDao.findBySku(skuId)

    suspend fun addWatch(skuId: Long, targetPriceCents: Long? = null): Long {
        val existing = watchDao.findBySku(skuId)
        if (existing != null) {
            watchDao.update(existing.copy(enabled = true, targetPriceCents = targetPriceCents))
            return existing.id
        }
        return watchDao.insert(
            WatchEntity(
                skuId = skuId,
                targetPriceCents = targetPriceCents,
                enabled = true,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setEnabled(watchId: Long, enabled: Boolean) {
        watchDao.get(watchId)?.let { watchDao.update(it.copy(enabled = enabled)) }
    }

    suspend fun setTargetPrice(watchId: Long, targetPriceCents: Long?) {
        watchDao.get(watchId)?.let { watchDao.update(it.copy(targetPriceCents = targetPriceCents)) }
    }

    suspend fun removeWatch(watchId: Long) {
        watchDao.get(watchId)?.let { watchDao.delete(it) }
    }

    suspend fun markAlertsRead() = alertDao.markAllRead()

    suspend fun clearAlerts() = alertDao.clear()

    /**
     * 定时检查：对每个启用的关注 SKU，挑当前最便宜且有货的链接重新查询。
     * 平台适配器不可用时跳过并记录原因，不会伪造报价。
     */
    suspend fun checkAll(): WatchCheckReport = withContextIo {
        val watches = watchDao.allEnabled()
        var fetched = 0
        var skipped = 0
        val alerts = mutableListOf<AlertEvent>()
        val notes = mutableListOf<String>()

        for (watch in watches) {
            val quotes = quoteDao.quotesOfSku(watch.skuId)
            if (quotes.isEmpty()) {
                skipped++
                notes += "关注 SKU #${watch.skuId} 还没有任何报价，无法检查"
                continue
            }

            val latestPerOffer = quotes.groupBy { it.offerKey }
                .mapNotNull { (_, list) -> list.maxByOrNull { it.capturedAt } }
            val target = latestPerOffer.filter { it.inStock }.minByOrNull { it.priceCents }
                ?: latestPerOffer.minByOrNull { it.priceCents }

            if (target == null || target.link.isBlank()) {
                skipped++
                notes += "关注 SKU #${watch.skuId} 缺少可查询链接"
                continue
            }

            val platform = Platform.fromKey(target.platform)
            val adapter = registry.get(platform)
            if (adapter == null || !adapter.capability.linkFetch) {
                skipped++
                notes += "${platform.displayName} 适配器未可用（无法自动查询），已跳过"
                continue
            }

            try {
                val snapshot = adapter.fetchByLink(target.link)
                val sku = priceRepository.getSku(watch.skuId)
                if (sku != null && snapshot.skuLabel != null) {
                    val fetchedSpec = SkuSpecParser.parse(snapshot.skuLabel)
                    if (fetchedSpec.normKey != "default" && sku.normSpec != "default" &&
                        fetchedSpec.normKey != sku.normSpec
                    ) {
                        skipped++
                        notes += "链接返回规格「${fetchedSpec.label}」与关注规格「${sku.displayName}」不一致，已跳过"
                        continue
                    }
                }
                val result = priceRepository.importOffers(
                    offers = listOf(snapshot),
                    forcedSkuId = watch.skuId
                )
                if (result.quotesInserted > 0) fetched++ else skipped++
                alerts += result.alerts
                notes += result.errors
            } catch (e: AdapterUnavailableException) {
                skipped++
                notes += "${platform.displayName}：${e.message}"
                Logger.e("watch check adapter unavailable", e)
            } catch (e: Exception) {
                skipped++
                notes += "${platform.displayName}：查询失败 ${e.message}"
                Logger.e("watch check failed", e)
            }
        }

        WatchCheckReport(
            watchCount = watches.size,
            fetched = fetched,
            skipped = skipped,
            alerts = alerts,
            notes = notes.distinct()
        )
    }

    private suspend fun <T> withContextIo(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            block()
        }
}
