package com.pricelens.data.repo

import com.pricelens.data.db.AlertEntity
import com.pricelens.data.db.AppDatabase
import com.pricelens.data.db.WatchEntity
import com.pricelens.domain.service.WatchEvaluator

data class AlertEvent(
    val alertId: Long,
    val watchId: Long,
    val skuId: Long,
    val spuTitle: String,
    val skuName: String,
    val quoteId: Long,
    val oldPriceCents: Long?,
    val newPriceCents: Long,
    val platform: String,
    val shopName: String?
)

/**
 * 批量导入 / 定时检查后统一评估关注项。
 * 判定的是「同一 SKU 在批次内最低有货价」是否严格低于「批次之前的最低有货价」。
 * 因此：更低价但缺货 → 不提醒；有货但价格没变 → 不提醒。
 */
class AlertService(private val db: AppDatabase) {

    private val watchDao = db.watchDao()
    private val alertDao = db.alertDao()
    private val quoteDao = db.quoteDao()
    private val skuDao = db.skuDao()
    private val spuDao = db.spuDao()

    suspend fun evaluateBatch(
        skuIds: Set<Long>,
        from: Long,
        to: Long,
        alertOnlyBelowTarget: Boolean
    ): List<AlertEvent> {
        if (skuIds.isEmpty()) return emptyList()
        val events = mutableListOf<AlertEvent>()

        for (skuId in skuIds) {
            val watch = watchDao.findBySku(skuId) ?: continue
            if (!watch.enabled) continue

            val previousPrice = quoteDao.minInStockPriceBefore(skuId, from)
            val newMinPrice = quoteDao.minInStockPriceBetween(skuId, from, to)
            val decision = WatchEvaluator.evaluate(
                previousMinPriceCents = previousPrice,
                newMinPriceCents = newMinPrice,
                inStock = newMinPrice != null,
                targetPriceCents = watch.targetPriceCents,
                alertOnlyBelowTarget = alertOnlyBelowTarget
            )

            if (!decision.shouldNotify) {
                watchDao.update(watch.copy(lastCheckedAt = System.currentTimeMillis()))
                continue
            }

            val cheapest = quoteDao.cheapestQuoteForSkuBetween(skuId, from, to) ?: continue
            val sku = skuDao.getSku(skuId)
            val spu = sku?.let { spuDao.getSpu(it.spuId) }

            val alertId = alertDao.insert(
                AlertEntity(
                    watchId = watch.id,
                    skuId = skuId,
                    quoteId = cheapest.id,
                    oldPriceCents = decision.previousPriceCents,
                    newPriceCents = cheapest.priceCents,
                    platform = cheapest.platform,
                    shopName = cheapest.shopName,
                    createdAt = System.currentTimeMillis()
                )
            )

            watchDao.update(
                watch.copy(
                    lastCheckedAt = System.currentTimeMillis(),
                    lastNotifiedPriceCents = cheapest.priceCents
                )
            )

            events += AlertEvent(
                alertId = alertId,
                watchId = watch.id,
                skuId = skuId,
                spuTitle = spu?.title ?: "未知商品",
                skuName = sku?.displayName ?: "未知规格",
                quoteId = cheapest.id,
                oldPriceCents = decision.previousPriceCents,
                newPriceCents = cheapest.priceCents,
                platform = cheapest.platform,
                shopName = cheapest.shopName
            )
        }

        return events
    }

    suspend fun updateCheckTime(watch: WatchEntity) {
        watchDao.update(watch.copy(lastCheckedAt = System.currentTimeMillis()))
    }
}
