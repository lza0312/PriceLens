package com.pricelens.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pricelens.core.Money
import com.pricelens.core.TimeFmt
import com.pricelens.data.AppContainer
import com.pricelens.data.db.WatchEntity
import com.pricelens.data.prefs.AppSettings
import com.pricelens.domain.model.QuoteView
import com.pricelens.domain.model.ScoredQuote
import com.pricelens.domain.model.SkuView
import com.pricelens.domain.model.TrendPoint
import com.pricelens.domain.service.ValueScore
import com.pricelens.ui.components.EmptyHint
import com.pricelens.ui.components.InfoBanner
import com.pricelens.ui.components.LinkButton
import com.pricelens.ui.components.MoneyText
import com.pricelens.ui.components.PlatformChip
import com.pricelens.ui.components.PriceTrendChart
import com.pricelens.ui.components.SectionCard
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun ProductDetailScreen(
    spuId: Long,
    onBack: () -> Unit,
    onOpenCompare: () -> Unit
) {
    val repo = AppContainer.priceRepository
    val watchRepo = AppContainer.watchRepository
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    val skus by repo.observeSkuViews(spuId).collectAsState(initial = emptyList())
    var selectedSkuId by remember { mutableStateOf<Long?>(null) }
    var trend by remember { mutableStateOf<List<TrendPoint>>(emptyList()) }
    var watch by remember { mutableStateOf<WatchEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var showWatchDialog by remember { mutableStateOf(false) }

    val settings by AppContainer.settingsStore.settings.collectAsState(initial = AppSettings())

    LaunchedEffect(spuId) {
        title = repo.getSpuTitle(spuId)
        category = AppContainer.database.spuDao().getSpu(spuId)?.category
    }

    LaunchedEffect(skus) {
        if (selectedSkuId == null || skus.none { it.skuId == selectedSkuId }) {
            selectedSkuId = skus.firstOrNull()?.skuId
        }
    }

    val quotesFlow = remember(selectedSkuId) {
        selectedSkuId?.let { repo.observeQuotesOfSku(it) } ?: flowOf(emptyList())
    }
    val quotes by quotesFlow.collectAsState(initial = emptyList())

    LaunchedEffect(selectedSkuId) {
        trend = selectedSkuId?.let { repo.trendPoints(it) } ?: emptyList()
        watch = selectedSkuId?.let { watchRepo.findWatch(it) }
    }

    val scored = remember(quotes, settings) {
        ValueScore.score(quotes, settings.weightPrice, settings.weightRating, settings.weightSales)
    }
    val selectedSku = skus.firstOrNull { it.skuId == selectedSkuId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title.ifBlank { "商品详情" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    category?.takeIf { it.isNotBlank() } ?: "未分类",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onOpenCompare) { Text("对比") }
        }

        SectionCard("规格（SKU）") {
            if (skus.isEmpty()) {
                EmptyHint("还没有规格数据")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(skus, key = { it.skuId }) { sku ->
                        FilterChip(
                            selected = sku.skuId == selectedSkuId,
                            onClick = { selectedSkuId = sku.skuId },
                            label = {
                                Text("${sku.displayName} · ${Money.format(sku.minPriceCents)}")
                            }
                        )
                    }
                }
                Text(
                    "共 ${skus.size} 个规格；每个规格独立报价与关注。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (selectedSku != null) {
            SectionCard("规格价格区间") {
                Row(verticalAlignment = Alignment.Bottom) {
                    MoneyText(selectedSku.minPriceCents, emphasize = true)
                    if (selectedSku.minPriceCents != selectedSku.maxPriceCents) {
                        Text(
                            "  ~  ${Money.format(selectedSku.maxPriceCents)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${selectedSku.quoteCount} 条报价",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionCard("性价比推荐") {
                if (scored.isEmpty()) {
                    EmptyHint("暂无可评分的有货报价")
                } else {
                    scored.take(3).forEachIndexed { index, item ->
                        ScoredRow(rank = index + 1, item = item)
                    }
                    Text(
                        "评分权重：价格 ${(settings.weightPrice * 100).toInt()}% / " +
                            "店铺评分 ${(settings.weightRating * 100).toInt()}% / " +
                            "销量 ${(settings.weightSales * 100).toInt()}%（可在「状态」页调整）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionCard("该规格报价（价格从低到高）") {
                if (quotes.isEmpty()) {
                    EmptyHint("该规格还没有报价")
                } else {
                    quotes.forEach { quote -> QuoteRow(quote) }
                }
            }

            SectionCard("价格趋势") {
                PriceTrendChart(trend)
            }

            SectionCard("关注与降价提醒") {
                Text(
                    "规则：只有该 SKU 有货且价格严格低于上次记录才提醒；" +
                        "更低价但缺货不提醒；价格没变不提醒；首次记录不提醒。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (watch == null) {
                        Text("未关注该规格", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            "已关注" + (watch?.targetPriceCents?.let { "，目标价 ${Money.format(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (watch == null) {
                        OutlinedButton(onClick = { showWatchDialog = true }) { Text("关注此规格") }
                    } else {
                        OutlinedButton(onClick = {
                            scope.launch {
                                watchRepo.removeWatch(watch!!.id)
                                watch = null
                                message = "已取消关注"
                            }
                        }) { Text("取消关注") }
                    }
                }
                watch?.lastCheckedAt?.takeIf { it > 0 }?.let {
                    Text(
                        "最近检查：${TimeFmt.dateTime(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionCard("同款横向对比") {
                Text(
                    "跨平台同款会按规格对齐；不同平台的报价保存在同一 SKU 下，可逐条比较。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onOpenCompare) { Text("打开横向对比表") }
            }
        }

        message?.let { InfoBanner(it, tone = com.pricelens.ui.components.BannerTone.INFO) }
    }

    if (showWatchDialog && selectedSku != null) {
        var targetInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showWatchDialog = false },
            title = { Text("关注「${selectedSku.displayName}」") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "目标价可选。留空表示「只要比上次低就提醒」。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it },
                        label = { Text("目标价（元，可选）") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val target = Money.parseToCents(targetInput)
                        val id = watchRepo.addWatch(selectedSku.skuId, target)
                        watch = watchRepo.findWatch(selectedSku.skuId) ?: WatchEntity(
                            id = id,
                            skuId = selectedSku.skuId,
                            enabled = true,
                            createdAt = System.currentTimeMillis()
                        )
                        showWatchDialog = false
                        message = "已关注该规格"
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showWatchDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ScoredRow(rank: Int, item: ScoredQuote) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("#$rank", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                PlatformChip(item.quote.platform)
                Text(
                    item.quote.shopName ?: "未知店铺",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${ValueScore.label(item.score)} ${(item.score * 100).toInt()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoneyText(item.quote.priceCents)
                Spacer(Modifier.weight(1f))
                Text(
                    "评分 ${item.quote.shopRating ?: "-"} · 销量 ${item.quote.salesCount ?: "-"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuoteRow(quote: QuoteView) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformChip(quote.platform)
                Text(
                    quote.shopName ?: "未知店铺",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                MoneyText(quote.priceCents)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (quote.inStock) "有货" else "无货",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (quote.inStock) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                Text(
                    "评分 ${quote.shopRating ?: "-"} · 销量 ${quote.salesCount ?: "-"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${quote.sourceKind.label} · ${TimeFmt.dateTime(quote.capturedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinkButton("打开商品页", quote.link)
        }
    }
}
