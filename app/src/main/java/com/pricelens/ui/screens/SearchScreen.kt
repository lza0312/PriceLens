package com.pricelens.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pricelens.collect.AdapterUnavailableException
import com.pricelens.data.AppContainer
import com.pricelens.domain.model.OfferSnapshot
import com.pricelens.domain.model.Platform
import com.pricelens.ui.components.BannerTone
import com.pricelens.ui.components.EmptyHint
import com.pricelens.ui.components.InfoBanner
import com.pricelens.ui.components.LinkButton
import com.pricelens.ui.components.MoneyText
import com.pricelens.ui.components.PlatformChip
import com.pricelens.ui.components.PlatformRiskBanner
import com.pricelens.ui.components.SectionCard
import kotlinx.coroutines.launch
import java.net.URLEncoder

private enum class SearchMode(val label: String, val hint: String) {
    KEYWORD("商品名 / 关键词", "例如：飞天茅台 53度 500ml"),
    CATEGORY("类目", "例如：手机 / 白酒 / 运动鞋（会作为类目关键词）"),
    SHOP("店铺名", "例如：贵州茅台酒京东自营旗舰店")
}

@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val registry = AppContainer.registry
    val repo = AppContainer.priceRepository

    var mode by remember { mutableStateOf(SearchMode.KEYWORD) }
    var query by remember { mutableStateOf("") }
    var selectedPlatforms by remember { mutableStateOf(setOf(Platform.JD)) }
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<OfferSnapshot>>(emptyList()) }
    var notes by remember { mutableStateOf<List<String>>(emptyList()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusTone by remember { mutableStateOf(BannerTone.INFO) }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) {
            statusMessage = "请输入${mode.label}"
            statusTone = BannerTone.WARNING
            return
        }
        if (selectedPlatforms.isEmpty()) {
            statusMessage = "请至少选择一个平台"
            statusTone = BannerTone.WARNING
            return
        }
        scope.launch {
            running = true
            statusMessage = null
            val collected = mutableListOf<OfferSnapshot>()
            val messages = mutableListOf<String>()
            for (platform in selectedPlatforms.sortedBy { it.ordinal }) {
                val adapter = registry.get(platform)
                if (adapter == null) {
                    messages += "${platform.displayName}：适配器未注册"
                    continue
                }
                val supported = when (mode) {
                    SearchMode.KEYWORD -> adapter.capability.keywordSearch
                    SearchMode.CATEGORY -> adapter.capability.categorySearch
                    SearchMode.SHOP -> adapter.capability.shopSearch
                }
                if (!supported) {
                    messages += "${platform.displayName}：${adapter.stateMessage}"
                    continue
                }
                try {
                    val items = when (mode) {
                        SearchMode.KEYWORD -> adapter.searchByKeyword(q)
                        SearchMode.CATEGORY -> adapter.searchByCategory(q)
                        SearchMode.SHOP -> adapter.searchByShop(q)
                    }
                    if (items.isEmpty()) {
                        messages += "${platform.displayName}：未返回结果"
                    } else {
                        collected += items
                        messages += "${platform.displayName}：返回 ${items.size} 条"
                    }
                } catch (e: AdapterUnavailableException) {
                    messages += "${platform.displayName} 适配器未可用：${e.message}"
                } catch (e: Exception) {
                    messages += "${platform.displayName} 查询失败：${e.message ?: e::class.java.simpleName}"
                }
            }
            results = collected
            notes = messages
            running = false
            if (collected.isEmpty()) {
                statusMessage = "没有拿到任何真实结果，未导入任何数据"
                statusTone = BannerTone.ERROR
            } else {
                statusMessage = "获取到 ${collected.size} 条真实结果，可导入本地"
                statusTone = BannerTone.SUCCESS
            }
        }
    }

    fun importResults() {
        if (results.isEmpty()) return
        scope.launch {
            val outcome = repo.importOffers(
                offers = results,
                defaultCategory = if (mode == SearchMode.CATEGORY) query.trim() else null
            )
            val extra = if (outcome.alerts.isNotEmpty()) "，触发 ${outcome.alerts.size} 条降价提醒" else ""
            statusMessage = "已导入：新增 ${outcome.quotesInserted} 条报价，跳过 ${outcome.duplicatesSkipped} 条" +
                "，新增 ${outcome.spusCreated} 个商品 / ${outcome.skusCreated} 个规格$extra"
            statusTone = if (outcome.quotesInserted > 0) BannerTone.SUCCESS else BannerTone.WARNING
            if (outcome.errors.isNotEmpty()) notes = outcome.errors.take(6)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("搜索采集", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "三个入口互相独立：关键词、类目、店铺名分别使用对应能力；平台不支持时会如实报错。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PlatformRiskBanner()

        SectionCard("搜索入口") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchMode.entries.forEach { item ->
                    FilterChip(
                        selected = mode == item,
                        onClick = {
                            mode = item
                            statusMessage = null
                        },
                        label = { Text(item.label) }
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(itemLabel(mode)) },
                placeholder = { Text(mode.hint) },
                singleLine = true
            )

            Text(
                "选择平台（可多选）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Platform.entries.filter { it != Platform.MANUAL }, key = { it.key }) { platform ->
                    FilterChip(
                        selected = platform in selectedPlatforms,
                        onClick = {
                            selectedPlatforms = if (platform in selectedPlatforms) {
                                selectedPlatforms - platform
                            } else {
                                selectedPlatforms + platform
                            }
                        },
                        label = { Text(platform.displayName) }
                    )
                }
            }

            Button(onClick = { search() }, enabled = !running) {
                Text(if (running) "查询中…" else "开始搜索")
            }
        }

        statusMessage?.let { InfoBanner(it, tone = statusTone) }

        if (notes.isNotEmpty()) {
            SectionCard("适配器返回情况") {
                notes.forEach { note ->
                    Text(note, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (results.isNotEmpty()) {
            SectionCard("搜索结果（${results.size} 条）") {
                Button(onClick = { importResults() }) { Text("全部导入本地") }
                results.take(30).forEach { offer -> SearchResultRow(offer) }
                if (results.size > 30) {
                    EmptyHint("仅展示前 30 条，导入时仍会处理全部 ${results.size} 条")
                }
            }
        }

        SectionCard("平台站内搜索（浏览器）") {
            Text(
                "适配器不可用时，可用系统浏览器打开平台自己的搜索页；登录与风控由平台页面处理，本应用不参与。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Platform.entries.filter { it != Platform.MANUAL }, key = { it.key }) { platform ->
                    OutlinedButton(
                        onClick = {
                            val q = query.trim().ifBlank { "商品" }
                            val target = webSearchUrl(platform, q) ?: return@OutlinedButton
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                            }
                        }
                    ) { Text(platform.displayName) }
                }
            }
        }
    }
}

private fun itemLabel(mode: SearchMode): String = when (mode) {
    SearchMode.KEYWORD -> "商品名 / 关键词"
    SearchMode.CATEGORY -> "类目名称"
    SearchMode.SHOP -> "店铺名称"
}

private fun webSearchUrl(platform: Platform, query: String): String? {
    val q = URLEncoder.encode(query, "UTF-8")
    return when (platform) {
        Platform.JD -> "https://search.jd.com/Search?keyword=$q"
        Platform.TAOBAO -> "https://s.taobao.com/search?q=$q"
        Platform.TMALL -> "https://list.tmall.com/search_product.htm?q=$q"
        Platform.PDD -> "https://mobile.yangkeduo.com/search_result.html?search_key=$q"
        Platform.DOUYIN -> "https://www.douyin.com"
        Platform.WEIDIAN -> "https://weidian.com"
        Platform.ALI1688 -> "https://s.1688.com/selloffer/offer_search.htm?keywords=$q"
        Platform.MANUAL -> null
    }
}

@Composable
private fun SearchResultRow(offer: OfferSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformChip(offer.platform)
                Text(
                    offer.productTitle,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoneyText(offer.priceCents, emphasize = true)
                Spacer(Modifier.size(8.dp))
                Text(
                    (offer.skuLabel?.let { "$it · " } ?: "") +
                        (if (offer.inStock) "有货" else "无货"),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (offer.inStock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "评分 ${offer.shopRating ?: "-"} · 销量 ${offer.salesCount ?: "-"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    offer.shopName ?: "未知店铺",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                LinkButton("打开商品页", offer.link)
            }
        }
    }
}
