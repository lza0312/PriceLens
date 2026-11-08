package com.pricelens.collect.linking

import com.pricelens.domain.model.Platform

data class ParsedShare(
    val platform: Platform?,
    val url: String?,
    val rawText: String,
    val warning: String? = null
)

/**
 * 分享文本解析（纯离线，不发请求）。
 * 支持直接 URL、京东/淘宝等分享文案里的 URL，以及无法离线解析的淘口令提示。
 */
object ShareLinkParser {

    private val urlRegex = Regex("https?://[A-Za-z0-9./?=_&%#:+~-]+")

    private val shortLinkHosts = setOf(
        "u.jd.com", "3.cn", "m.tb.cn", "e.tb.cn", "v.douyin.com",
        "p.pinduoduo.com", "koudai.com", "m.1688.com"
    )

    fun parse(rawText: String): List<ParsedShare> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val urls = urlRegex.findAll(trimmed).map { it.value.trimEnd('。', '，', '）', ')') }.toList()
        if (urls.isNotEmpty()) {
            return urls.map { url ->
                val platform = detectPlatform(url)
                ParsedShare(
                    platform = platform,
                    url = url,
                    rawText = trimmed,
                    warning = if (platform == null) "未能识别链接所属平台，可手动选择平台后补录" else null
                )
            }
        }

        val isTaobaoToken = Regex("(￥|\\$)[A-Za-z0-9]{8,}(￥|\\$)").containsMatchIn(trimmed) ||
            trimmed.contains("淘口令") || trimmed.contains("复制打开淘宝")
        if (isTaobaoToken) {
            return listOf(
                ParsedShare(
                    platform = Platform.TAOBAO,
                    url = null,
                    rawText = trimmed,
                    warning = "淘口令需要淘宝 App 才能打开，离线无法解析出商品链接；请用「手动补录」填写链接与价格"
                )
            )
        }

        return listOf(
            ParsedShare(
                platform = null,
                url = null,
                rawText = trimmed,
                warning = "文本中未找到商品链接。支持 http(s) 链接与常见分享文案。"
            )
        )
    }

    fun detectPlatform(url: String): Platform? {
        val u = url.lowercase()
        return when {
            u.contains("jd.com") || u.contains("3.cn") || u.contains("360buy") -> Platform.JD
            u.contains("tmall.com") -> Platform.TMALL
            u.contains("taobao.com") || u.contains("tb.cn") -> Platform.TAOBAO
            u.contains("yangkeduo.com") || u.contains("pinduoduo.com") -> Platform.PDD
            u.contains("douyin.com") || u.contains("jinritemai.com") -> Platform.DOUYIN
            u.contains("weidian.com") || u.contains("koudai.com") -> Platform.WEIDIAN
            u.contains("1688.com") -> Platform.ALI1688
            else -> null
        }
    }

    fun isShortLink(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
        return shortLinkHosts.any { host == it }
    }
}
