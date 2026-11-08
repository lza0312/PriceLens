package com.pricelens.domain.service

import com.pricelens.domain.model.Platform
import com.pricelens.domain.model.QuoteView
import com.pricelens.domain.model.ScoredQuote
import kotlin.math.log10

/**
 * 性价比评分：价格越低、评分越高、销量越大得分越高。
 * 权重来自设置，三项权重会自动归一化。
 */
object ValueScore {

    fun score(
        quotes: List<QuoteView>,
        weightPrice: Double = 0.60,
        weightRating: Double = 0.25,
        weightSales: Double = 0.15
    ): List<ScoredQuote> {
        val inStock = quotes.filter { it.inStock }
        if (inStock.isEmpty()) return emptyList()

        val minPrice = inStock.minOf { it.priceCents }
        val maxPrice = inStock.maxOf { it.priceCents }

        val wSum = (weightPrice + weightRating + weightSales).takeIf { it > 0 } ?: 1.0

        return inStock.map { quote ->
            val priceScore = if (maxPrice == minPrice) {
                1.0
            } else {
                (maxPrice - quote.priceCents).toDouble() / (maxPrice - minPrice)
            }
            val ratingScore = quote.shopRating?.let { (it / 5.0).coerceIn(0.0, 1.0) } ?: 0.5
            val salesScore = ((log10(1.0 + (quote.salesCount ?: 0L)) / 6.0).coerceIn(0.0, 1.0))
            val total = (priceScore * weightPrice + ratingScore * weightRating +
                salesScore * weightSales) / wSum
            ScoredQuote(
                quote = quote,
                score = total,
                priceScore = priceScore,
                ratingScore = ratingScore,
                salesScore = salesScore
            )
        }.sortedByDescending { it.score }
    }

    fun label(score: Double): String = when {
        score >= 0.85 -> "极佳"
        score >= 0.72 -> "推荐"
        score >= 0.58 -> "一般"
        else -> "偏低"
    }
}

/**
 * 跨平台同款对齐：按标题相似度 + 规格是否可对齐做分组。
 */
object SameProductAligner {

    fun align(
        candidates: List<QuoteView>,
        referenceTitle: String,
        threshold: Double = 0.70
    ): List<QuoteView> {
        val ref = com.pricelens.core.TextNormalizer.normalize(referenceTitle)
        if (ref.isEmpty()) return emptyList()
        return candidates.filter { quote ->
            val sim = com.pricelens.core.TextNormalizer.similarity(ref, quote.productTitle)
            sim >= threshold
        }
    }

    fun platformCount(quotes: List<QuoteView>): Int {
        return quotes.map { it.platform }.distinct().size
    }

    fun cheapestPerPlatform(quotes: List<QuoteView>): Map<Platform, QuoteView> {
        return quotes.filter { it.inStock }
            .groupBy { it.platform }
            .mapNotNull { (platform, list) ->
                val cheapest = list.minByOrNull { it.priceCents } ?: return@mapNotNull null
                platform to cheapest
            }.toMap()
    }
}
