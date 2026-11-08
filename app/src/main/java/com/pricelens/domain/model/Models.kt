package com.pricelens.domain.model

/**
 * 平台枚举。key 会写入数据库，改动后需要数据迁移。
 */
enum class Platform(val key: String, val displayName: String, val colorSeed: Int) {
    JD("jd", "京东", 0xC62828),
    TAOBAO("taobao", "淘宝", 0xEF6C00),
    TMALL("tmall", "天猫", 0xAD1457),
    PDD("pdd", "拼多多", 0xD32F2F),
    DOUYIN("douyin", "抖音商城", 0x111111),
    WEIDIAN("weidian", "微店", 0x00838F),
    ALI1688("1688", "1688", 0x2E7D32),
    MANUAL("manual", "手动录入", 0x546E7A);

    companion object {
        fun fromKey(key: String?): Platform =
            entries.firstOrNull { it.key == key } ?: MANUAL
    }
}

enum class SourceKind(val label: String) {
    SHARE_LINK("分享链接"),
    CLIPBOARD("剪贴板"),
    CSV("CSV 导入"),
    MANUAL("手动补录"),
    ADAPTER_SEARCH("适配器搜索"),
    ADAPTER_FETCH("适配器查询")
}

/**
 * 采集层统一输出结构（商品级 + 规格级）。
 * 没有任何真实来源时不允许构造此对象；适配器不可用必须抛异常。
 */
data class OfferSnapshot(
    val platform: Platform,
    val productTitle: String,
    val skuLabel: String?,
    val link: String,
    val priceCents: Long,
    val listPriceCents: Long? = null,
    val salesCount: Long? = null,
    val shopName: String? = null,
    val shopRating: Double? = null,
    val inStock: Boolean = true,
    val imageUrl: String? = null,
    val note: String? = null,
    val categoryHint: String? = null,
    val sourceKind: SourceKind = SourceKind.MANUAL
)

/** 规格解析结果：展示标签 + 归一化键 + 维度属性。 */
data class ParsedSpec(
    val label: String,
    val normKey: String,
    val attrs: Map<String, String> = emptyMap()
)

data class SkuView(
    val skuId: Long,
    val spuId: Long,
    val displayName: String,
    val normSpec: String,
    val quoteCount: Int,
    val minPriceCents: Long?,
    val maxPriceCents: Long?
)

data class QuoteView(
    val quoteId: Long,
    val skuId: Long,
    val platform: Platform,
    val offerKey: String,
    val productTitle: String,
    val link: String,
    val priceCents: Long,
    val listPriceCents: Long?,
    val salesCount: Long?,
    val shopName: String?,
    val shopRating: Double?,
    val inStock: Boolean,
    val sourceKind: SourceKind,
    val capturedAt: Long
)

data class SpuSummaryModel(
    val spuId: Long,
    val title: String,
    val category: String?,
    val skuCount: Int,
    val quoteCount: Int,
    val minPriceCents: Long?,
    val maxPriceCents: Long?,
    val bestPlatform: Platform?,
    val bestShop: String?,
    val bestLink: String?,
    val lastCapturedAt: Long
)

data class TrendPoint(val timestamp: Long, val priceCents: Long, val platform: Platform, val inStock: Boolean)

data class ComparisonRow(
    val specLabel: String,
    val quotes: List<QuoteView>,
    val bestQuote: QuoteView?
)

data class ScoredQuote(
    val quote: QuoteView,
    val score: Double,
    val priceScore: Double,
    val ratingScore: Double,
    val salesScore: Double
)

/** 关注判定结果，用于 UI 说明为什么提醒 / 不提醒。 */
data class WatchDecision(
    val shouldNotify: Boolean,
    val reason: String,
    val previousPriceCents: Long?,
    val newPriceCents: Long
)
