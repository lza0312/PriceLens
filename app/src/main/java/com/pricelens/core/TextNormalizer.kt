package com.pricelens.core

import com.pricelens.domain.model.Platform

/**
 * 标题归一化：全角转半角、去营销词、去表情、压缩空白。
 * 仅用于「同一商品判断」，不用于展示。
 */
object TextNormalizer {

    private val marketingWords = listOf(
        "包邮", "秒杀", "秒发", "现货", "新品", "正品", "官方旗舰店", "旗舰店", "自营",
        "限时", "特价", "券后", "优惠券", "领券", "百亿补贴", "补贴价", "到手价",
        "顺丰", "赠品", "买一送一", "下单立减", "官方", "直营", "国行", "全新",
        "支持花呗", "分期免息", "价保", "运费险", "七天无理由", "官方授权"
    )

    private val emojiRegex = Regex("[\\u2600-\\u27BF\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\uFE0F]")
    private val bracketRegex = Regex("[【\\[（(][^】\\]）)]{0,24}[】\\]）)]?")
    private val punctuation = Regex("[\\p{Punct}\\p{IsPunctuation}　]")

    fun normalize(raw: String): String {
        var text = fullToHalf(raw)
        text = emojiRegex.replace(text, "")
        text = bracketRegex.replace(text, " ")
        marketingWords.forEach { word -> text = text.replace(word, " ") }
        text = text.lowercase()
        text = punctuation.replace(text, " ")
        text = text.replace(Regex("\\s+"), " ").trim()
        return text
    }

    fun fullToHalf(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(
                when {
                    ch == '\u3000' -> ' '
                    ch.code in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar()
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    /**
     * 字符 bigram Jaccard 相似度，用于跨平台同款判断。
     * 两个空串视为不相似；短标题（<4 字）直接比较是否相等。
     */
    fun similarity(a: String, b: String): Double {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return 0.0
        if (na == nb) return 1.0
        if (na.length < 4 || nb.length < 4) return 0.0
        val gramsA = bigrams(na)
        val gramsB = bigrams(nb)
        if (gramsA.isEmpty() || gramsB.isEmpty()) return 0.0
        val inter = gramsA.intersect(gramsB).size
        val union = gramsA.union(gramsB).size
        return if (union == 0) 0.0 else inter.toDouble() / union
    }

    private fun bigrams(s: String): Set<String> {
        val set = HashSet<String>(s.length)
        for (i in 0 until s.length - 1) {
            set.add(s.substring(i, i + 2))
        }
        return set
    }

    fun shopKey(shopName: String?): String {
        if (shopName.isNullOrBlank()) return "-"
        return normalize(shopName).replace(" ", "")
    }

    /** 从链接中提取平台 seller/item 标识，用于把同一链接的历史报价串起来。 */
    fun offerKey(platform: Platform, link: String): String {
        val id = when (platform) {
            Platform.JD -> Regex("(?:item\\.jd\\.com/|skuId=J?_?)(\\d{6,})").find(link)?.groupValues?.get(1)
            Platform.TAOBAO, Platform.TMALL -> Regex("[?&]id=(\\d{6,})").find(link)?.groupValues?.get(1)
            Platform.PDD -> Regex("[?&]goods_id=(\\d{6,})").find(link)?.groupValues?.get(1)
            Platform.DOUYIN -> Regex("[?&]id=(\\d{6,})").find(link)?.groupValues?.get(1)
            Platform.WEIDIAN -> Regex("[?&]itemId=(\\d{4,})").find(link)?.groupValues?.get(1)
            Platform.ALI1688 -> Regex("offer/(\\d{6,})").find(link)?.groupValues?.get(1)
            Platform.MANUAL -> null
        }
        val suffix = id ?: link.trim().takeLast(24)
        return "${platform.key}#$suffix"
    }
}
