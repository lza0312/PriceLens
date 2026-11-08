package com.pricelens.data.repo

import com.pricelens.core.Money
import com.pricelens.core.TextNormalizer
import com.pricelens.data.db.AppDatabase
import com.pricelens.data.db.QuoteEntity
import com.pricelens.data.db.SkuEntity
import com.pricelens.data.db.SpuEntity
import com.pricelens.data.prefs.SettingsStore
import com.pricelens.domain.model.OfferSnapshot
import com.pricelens.domain.model.Platform
import com.pricelens.domain.model.QuoteView
import com.pricelens.domain.model.SkuView
import com.pricelens.domain.model.SourceKind
import com.pricelens.domain.model.SpuSummaryModel
import com.pricelens.domain.model.TrendPoint
import com.pricelens.domain.service.SkuSpecParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ImportResult(
    val offersReceived: Int = 0,
    val quotesInserted: Int = 0,
    val duplicatesSkipped: Int = 0,
    val spusCreated: Int = 0,
    val skusCreated: Int = 0,
    val alerts: List<AlertEvent> = emptyList(),
    val errors: List<String> = emptyList()
)

class PriceRepository(
    private val db: AppDatabase,
    private val settingsStore: SettingsStore
) {

    private val spuDao = db.spuDao()
    private val skuDao = db.skuDao()
    private val quoteDao = db.quoteDao()
    private val alertService = AlertService(db)
    private val mutex = Mutex()

    fun observeSummaries(): Flow<List<SpuSummaryModel>> =
        combine(spuDao.observeSummaries(), quoteDao.observeCheapestPerSpu()) { rows, cheapest ->
            val cheapestMap = cheapest.associateBy { it.spuId }
            rows.map { row ->
                val best = cheapestMap[row.spuId]
                SpuSummaryModel(
                    spuId = row.spuId,
                    title = row.title,
                    category = row.category,
                    skuCount = row.skuCount,
                    quoteCount = row.quoteCount,
                    minPriceCents = row.minPriceCents,
                    maxPriceCents = row.maxPriceCents,
                    bestPlatform = best?.let { Platform.fromKey(it.platform) },
                    bestShop = best?.shopName,
                    bestLink = best?.link,
                    lastCapturedAt = row.lastCapturedAt ?: 0L
                )
            }
        }.flowOn(Dispatchers.IO)

    fun observeSkuViews(spuId: Long): Flow<List<SkuView>> =
        skuDao.observeSkuStats(spuId).map { rows ->
            rows.map {
                SkuView(
                    skuId = it.skuId,
                    spuId = it.spuId,
                    displayName = it.displayName,
                    normSpec = it.normSpec,
                    quoteCount = it.quoteCount,
                    minPriceCents = it.minPriceCents,
                    maxPriceCents = it.maxPriceCents
                )
            }
        }.flowOn(Dispatchers.IO)

    fun observeQuotesOfSku(skuId: Long): Flow<List<QuoteView>> =
        quoteDao.observeQuotesOfSku(skuId).map { list -> list.map { it.toView() } }
            .flowOn(Dispatchers.IO)

    fun observeQuotesOfSpu(spuId: Long): Flow<List<QuoteView>> =
        quoteDao.observeQuotesOfSpu(spuId).map { list -> list.map { it.toView() } }
            .flowOn(Dispatchers.IO)

    fun observeSpu(spuId: Long): Flow<SpuEntity?> = spuDao.observeSpu(spuId)

    suspend fun getSpuTitle(spuId: Long): String = spuDao.getSpu(spuId)?.title ?: "未知商品"

    suspend fun getSku(skuId: Long): SkuEntity? = skuDao.getSku(skuId)

    /**
     * 批量写入报价，并触发关注判定。
     * 同平台、同链接、同价格、6 小时内的重复记录会被跳过（可通过设置关闭严格去重）。
     */
    suspend fun importOffers(
        offers: List<OfferSnapshot>,
        defaultCategory: String? = null,
        forcedSkuId: Long? = null
    ): ImportResult = mutex.withLock {
        val settings = settingsStore.settings.first()
        val now = System.currentTimeMillis()
        var inserted = 0
        var skipped = 0
        var spusCreated = 0
        var skusCreated = 0
        val affectedSkus = mutableSetOf<Long>()
        val touchedSpus = mutableSetOf<Long>()
        val errors = mutableListOf<String>()

        for (offer in offers) {
            if (offer.priceCents <= 0) {
                skipped++
                errors += "「${offer.productTitle.take(20)}」价格无效，已跳过"
                continue
            }
            val rawTitle = offer.productTitle.trim()
            if (rawTitle.isEmpty()) {
                skipped++
                errors += "商品名称为空，已跳过"
                continue
            }
            val normalized = TextNormalizer.normalize(rawTitle).ifEmpty { rawTitle.lowercase() }
            val category = offer.categoryHint ?: defaultCategory

            val spuResult: ResolvedSpu
            val skuResult: ResolvedSku
            if (forcedSkuId != null) {
                val sku = skuDao.getSku(forcedSkuId)
                if (sku == null) {
                    skipped++
                    errors += "关注规格已不存在，已跳过「$rawTitle」"
                    continue
                }
                skuResult = ResolvedSku(sku, false)
                spuResult = ResolvedSpu(spuDao.getSpu(sku.spuId) ?: continue, false)
            } else {
                spuResult = resolveSpu(normalized, rawTitle, category, now, settings.dedupeStrict)
                if (spuResult.created) spusCreated++
                val parsedSpec = SkuSpecParser.parse(offer.skuLabel ?: rawTitle)
                skuResult = resolveSku(
                    spuResult.spu,
                    parsedSpec.label,
                    parsedSpec.normKey,
                    parsedSpec.attrs,
                    now
                )
                if (skuResult.created) skusCreated++
            }

            val offerKey = TextNormalizer.offerKey(offer.platform, offer.link.ifBlank { "${offer.shopName}-${rawTitle}" })

            if (isDuplicate(skuResult.sku.id, offer, offerKey, now, settings.dedupeStrict)) {
                skipped++
                continue
            }

            quoteDao.insert(
                QuoteEntity(
                    skuId = skuResult.sku.id,
                    platform = offer.platform.key,
                    offerKey = offerKey,
                    link = offer.link,
                    productTitle = rawTitle,
                    priceCents = offer.priceCents,
                    listPriceCents = offer.listPriceCents,
                    salesCount = offer.salesCount,
                    shopName = offer.shopName,
                    shopRating = offer.shopRating,
                    inStock = offer.inStock,
                    skuLabel = offer.skuLabel,
                    sourceKind = offer.sourceKind.name,
                    imageUrl = offer.imageUrl,
                    note = offer.note,
                    capturedAt = now
                )
            )
            inserted++
            affectedSkus += skuResult.sku.id
            touchedSpus += spuResult.spu.id
        }

        if (touchedSpus.isNotEmpty()) spuDao.touch(touchedSpus.toList(), now)

        val alerts = alertService.evaluateBatch(
            skuIds = affectedSkus,
            from = now,
            to = now,
            alertOnlyBelowTarget = settings.alertOnlyBelowTarget
        )

        ImportResult(
            offersReceived = offers.size,
            quotesInserted = inserted,
            duplicatesSkipped = skipped,
            spusCreated = spusCreated,
            skusCreated = skusCreated,
            alerts = alerts,
            errors = errors
        )
    }

    private data class ResolvedSpu(val spu: SpuEntity, val created: Boolean)
    private data class ResolvedSku(val sku: SkuEntity, val created: Boolean)

    private suspend fun resolveSpu(
        normalizedTitle: String,
        rawTitle: String,
        category: String?,
        now: Long,
        strict: Boolean
    ): ResolvedSpu {
        spuDao.findByNormalizedTitle(normalizedTitle).firstOrNull()?.let {
            return ResolvedSpu(it, false)
        }
        if (strict) {
            val recent = spuDao.recentSpus(200)
            val matched = recent.firstOrNull {
                TextNormalizer.similarity(it.normalizedTitle, normalizedTitle) >= 0.86
            }
            if (matched != null) return ResolvedSpu(matched, false)
        }
        val id = spuDao.insert(
            SpuEntity(
                title = rawTitle,
                normalizedTitle = normalizedTitle,
                category = category,
                createdAt = now,
                updatedAt = now
            )
        )
        return ResolvedSpu(spuDao.getSpu(id)!!, true)
    }

    private suspend fun resolveSku(
        spu: SpuEntity,
        displayName: String,
        normSpec: String,
        attrs: Map<String, String>,
        now: Long
    ): ResolvedSku {
        skuDao.findBySpec(spu.id, normSpec)?.let { return ResolvedSku(it, false) }
        val specJson = attrs.entries.joinToString("&") { "${it.key}=${it.value}" }
        val id = skuDao.insert(
            SkuEntity(
                spuId = spu.id,
                displayName = displayName,
                normSpec = normSpec,
                specJson = specJson.ifEmpty { null },
                createdAt = now
            )
        )
        return ResolvedSku(skuDao.getSku(id)!!, true)
    }

    private suspend fun isDuplicate(
        skuId: Long,
        offer: OfferSnapshot,
        offerKey: String,
        now: Long,
        strict: Boolean
    ): Boolean {
        val window = if (strict) 6L * 60 * 60 * 1000 else 60L * 60 * 1000
        val recent = quoteDao.quotesSince(skuId, now - window)

        return recent.any { existing ->
            val samePrice = existing.priceCents == offer.priceCents && existing.inStock == offer.inStock
            if (!samePrice) return@any false
            val sameOffer = existing.offerKey == offerKey
            val sameShopAndTitle = TextNormalizer.shopKey(existing.shopName) ==
                TextNormalizer.shopKey(offer.shopName) &&
                TextNormalizer.similarity(existing.productTitle, offer.productTitle) > 0.9
            existing.platform == offer.platform.key && (sameOffer || sameShopAndTitle)
        }
    }

    /** 按天聚合的最低有货价，用于趋势图。 */
    suspend fun trendPoints(skuId: Long, inStockOnly: Boolean = true): List<TrendPoint> {
        val quotes = quoteDao.trendsOfSku(skuId)
        val grouped = quotes
            .filter { if (inStockOnly) it.inStock else true }
            .groupBy { it.capturedAt / (24 * 60 * 60 * 1000) }
        return grouped.map { (day, list) ->
            val best = list.minByOrNull { it.priceCents }!!
            TrendPoint(
                timestamp = day * 24 * 60 * 60 * 1000,
                priceCents = best.priceCents,
                platform = Platform.fromKey(best.platform),
                inStock = best.inStock
            )
        }.sortedBy { it.timestamp }
    }

    /** 同一链接的历史价格（用于单链接趋势）。 */
    suspend fun offerHistory(offerKey: String): List<QuoteView> =
        quoteDao.historyOfOffer(offerKey).map { it.toView() }

    private fun QuoteEntity.toView(): QuoteView = QuoteView(
        quoteId = id,
        skuId = skuId,
        platform = Platform.fromKey(platform),
        offerKey = offerKey,
        productTitle = productTitle,
        link = link,
        priceCents = priceCents,
        listPriceCents = listPriceCents,
        salesCount = salesCount,
        shopName = shopName,
        shopRating = shopRating,
        inStock = inStock,
        sourceKind = runCatching { SourceKind.valueOf(sourceKind) }.getOrDefault(SourceKind.MANUAL),
        capturedAt = capturedAt
    )

    fun formatPrice(cents: Long?): String = Money.format(cents)
}
