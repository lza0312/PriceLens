package com.pricelens.collect.adapters

import com.pricelens.collect.AdapterCapability
import com.pricelens.collect.AdapterState
import com.pricelens.collect.AdapterUnavailableException
import com.pricelens.collect.PlatformAdapter
import com.pricelens.collect.net.HttpClient
import com.pricelens.core.Money
import com.pricelens.core.TextNormalizer
import com.pricelens.domain.model.OfferSnapshot
import com.pricelens.domain.model.Platform
import com.pricelens.domain.model.SourceKind

/**
 * 京东适配器（第一个跑通的平台）。
 *
 * 实测结论（2026-11）：
 * - `https://item.m.jd.com/ware/product/{skuId}.html` 可公开访问，返回真实标题、`"jdPrice":"1699.00"`、`"shopName":"..."`。
 * - `https://search.jd.com/Search?keyword=...` 返回约 2.6KB 的验证页，无商品节点，因此关键词/类目/店铺搜索标记为不可用。
 *
 * 失效风险：
 * - 页面结构或内嵌 JSON 字段变更（目前只依赖 3 个正则）；
 * - 无 Cookie 请求可能被风控，返回验证页 / 掩码价格（例如 `1??9`）；
 * - 部分商品（预约、抢购、多 SKU）页面价格与真实下单价不一致；
 * - 销量未公开，`salesCount` 保持为空，不做估算。
 *
 * 权限：仅 INTERNET。无需登录，也就无法获取需要登录才可见的会员价。
 */
class JdAdapter(private val http: HttpClient) : PlatformAdapter {

    override val platform: Platform = Platform.JD

    override val state: AdapterState = AdapterState.PARTIAL

    override val capability: AdapterCapability = AdapterCapability(
        keywordSearch = false,
        categorySearch = false,
        shopSearch = false,
        linkFetch = true
    )

    override val stateMessage: String =
        "仅支持商品链接查询（公开页）。关键词搜索被京东验证页拦截，未实现。"

    override val riskNotes: String =
        "依赖京东移动端公开商品页的页面结构；字段变更或风控（验证页、价格掩码）会立即失效。" +
            "无 Cookie 时可能看不到会员价/区域价；销量不公开，不做估算。" +
            "使用采集功能即表示你自行承担平台用户协议与账号风险。"

    override val requiredPermissions: String = "INTERNET"

    override suspend fun fetchByLink(url: String): OfferSnapshot {
        val skuId = extractSkuId(url)
            ?: throw AdapterUnavailableException(
                platform,
                "无法从链接中识别京东商品 ID，请确认是 item.jd.com / item.m.jd.com 的商品链接"
            )

        val pageUrl = "https://item.m.jd.com/ware/product/$skuId.html"
        val html = http.getText(pageUrl, referer = "https://item.jd.com/", mobile = true)
            ?: throw AdapterUnavailableException(platform, "商品页请求失败（网络或风控），未获取到数据")

        if (html.length < 20_000) {
            throw AdapterUnavailableException(
                platform,
                "商品页返回 ${html.length} 字节，疑似被风控/验证页拦截，未获取到数据"
            )
        }

        val title = parseTitle(html)
            ?: throw AdapterUnavailableException(platform, "未能解析商品标题，页面结构可能已变更")
        if (title.contains("验证") || title.contains("京东(JD.COM)")) {
            throw AdapterUnavailableException(platform, "返回的是验证页而非商品页，未获取到数据")
        }

        val priceText = PRICE_REGEX.find(html)?.groupValues?.getOrNull(1)
            ?: throw AdapterUnavailableException(
                platform,
                "页面未返回可用价格（可能被掩码或需要登录），未生成报价"
            )
        val priceCents = Money.parseToCents(priceText)
            ?: throw AdapterUnavailableException(platform, "价格字段无法解析：$priceText")

        val listPrice = LIST_PRICE_REGEX.find(html)?.groupValues?.getOrNull(1)
        val shopName = SHOP_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

        val soldOut = html.contains("无货") || html.contains("售罄") || html.contains("已下柜")

        return OfferSnapshot(
            platform = Platform.JD,
            productTitle = title,
            skuLabel = parseSkuLabel(html),
            link = "https://item.jd.com/$skuId.html",
            priceCents = priceCents,
            listPriceCents = Money.parseToCents(listPrice),
            salesCount = null,
            shopName = shopName,
            shopRating = null,
            inStock = !soldOut,
            sourceKind = SourceKind.ADAPTER_FETCH,
            note = "京东公开页解析；销量未公开"
        )
    }

    override suspend fun searchByKeyword(query: String, limit: Int): List<OfferSnapshot> =
        throw AdapterUnavailableException(
            platform,
            "京东搜索页需要登录并通过验证码/风控校验，实测仅返回 2.6KB 验证页；当前不可用。可改用分享链接查询。"
        )

    override suspend fun searchByCategory(category: String, limit: Int): List<OfferSnapshot> =
        throw AdapterUnavailableException(platform, "类目检索需要登录态与类目 ID 映射，当前不可用")

    override suspend fun searchByShop(shopName: String, limit: Int): List<OfferSnapshot> =
        throw AdapterUnavailableException(platform, "店铺搜索需要登录态，当前不可用")

    private fun extractSkuId(url: String): String? {
        val patterns = listOf(
            Regex("item\\.jd\\.com/(?:ware/product/)?(\\d{6,})"),
            Regex("item\\.m\\.jd\\.com/(?:product|ware/product)/(\\d{6,})"),
            Regex("[?&]skuId=(\\d{6,})"),
            Regex("[?&]wareId=(\\d{6,})")
        )
        return patterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.getOrNull(1) }
    }

    private fun parseTitle(html: String): String? {
        val raw = TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim() ?: return null
        val cleaned = raw
            .replace("【图片 价格 品牌 评论】", "")
            .replace("-京东", "")
            .replace("&nbsp;", " ")
            .trim()
        return TextNormalizer.fullToHalf(cleaned).takeIf { it.isNotBlank() }
    }

    private fun parseSkuLabel(html: String): String? {
        val match = Regex(""""skuName":"([^"]{1,60})"""").find(html) ?: return null
        return match.groupValues.getOrNull(1)?.replace("\\u002F", "/")
    }

    companion object {
        private val TITLE_REGEX = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
        private val PRICE_REGEX = Regex(""""jdPrice"\s*:\s*"([0-9.]+)"""")
        private val LIST_PRICE_REGEX = Regex(""""salePrice"\s*:\s*"([0-9.]+)"""")
        private val SHOP_REGEX = Regex(""""shopName"\s*:\s*"([^"]{1,60})"""")
    }
}
