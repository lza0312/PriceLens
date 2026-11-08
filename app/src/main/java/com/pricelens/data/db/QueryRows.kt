package com.pricelens.data.db

/** Room 查询投影（POJO），字段必须与 SQL 别名一致。 */
data class SpuSummaryRow(
    val spuId: Long,
    val title: String,
    val category: String?,
    val skuCount: Int,
    val quoteCount: Int,
    val minPriceCents: Long?,
    val maxPriceCents: Long?,
    val lastCapturedAt: Long?
)

data class CheapestOfferRow(
    val spuId: Long,
    val platform: String,
    val shopName: String?,
    val link: String,
    val priceCents: Long
)

data class SkuStatsRow(
    val skuId: Long,
    val spuId: Long,
    val displayName: String,
    val normSpec: String,
    val quoteCount: Int,
    val minPriceCents: Long?,
    val maxPriceCents: Long?
)

data class WatchRow(
    val watchId: Long,
    val skuId: Long,
    val spuId: Long,
    val spuTitle: String,
    val skuName: String,
    val targetPriceCents: Long?,
    val enabled: Boolean,
    val lastCheckedAt: Long,
    val lastPriceCents: Long?,
    val quoteCount: Int
)

data class AlertRow(
    val alertId: Long,
    val watchId: Long,
    val spuTitle: String,
    val skuName: String,
    val oldPriceCents: Long?,
    val newPriceCents: Long,
    val platform: String,
    val shopName: String?,
    val createdAt: Long,
    val read: Boolean
)

/** 导出用扁平行。 */
data class ExportQuoteRow(
    val spuTitle: String,
    val category: String?,
    val skuName: String,
    val platform: String,
    val productTitle: String,
    val priceCents: Long,
    val listPriceCents: Long?,
    val salesCount: Long?,
    val shopName: String?,
    val shopRating: Double?,
    val inStock: Boolean,
    val link: String,
    val sourceKind: String,
    val capturedAt: Long,
    val offerKey: String
)
