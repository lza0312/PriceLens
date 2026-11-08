package com.pricelens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pricelens.core.Money
import com.pricelens.data.AppContainer
import com.pricelens.domain.model.QuoteView
import com.pricelens.domain.model.SkuView
import com.pricelens.ui.components.EmptyHint
import com.pricelens.ui.components.LinkButton
import com.pricelens.ui.components.PlatformChip

/**
 * 商品横向对比：按 SKU 行对齐，同一规格下逐条比较不同平台 / 店铺的报价。
 */
@Composable
fun CompareScreen(spuId: Long, onBack: () -> Unit) {
    val repo = AppContainer.priceRepository
    val skus by repo.observeSkuViews(spuId).collectAsState(initial = emptyList())
    val allQuotes by repo.observeQuotesOfSpu(spuId).collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(spuId) { title = repo.getSpuTitle(spuId) }

    val latestBySku = remember(allQuotes) {
        allQuotes.groupBy { it.skuId }.mapValues { (_, list) ->
            list.groupBy { it.offerKey }
                .mapNotNull { (_, sameOffer) -> sameOffer.maxByOrNull { it.capturedAt } }
                .sortedBy { it.priceCents }
        }
    }

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
                Text("横向对比", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (skus.isEmpty()) {
            EmptyHint("没有可对比的规格")
        }

        skus.forEach { sku ->
            val quotes = latestBySku[sku.skuId].orEmpty()
            SkuCompareCard(sku, quotes)
        }
    }
}

@Composable
private fun SkuCompareCard(sku: SkuView, quotes: List<QuoteView>) {
    val inStock = quotes.filter { it.inStock }
    val minPrice = inStock.minOfOrNull { it.priceCents }
    val maxPrice = inStock.maxOfOrNull { it.priceCents }
    val platforms = quotes.map { it.platform }.distinct()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sku.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                "${platforms.size} 平台 · ${quotes.size} 条链接",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (quotes.isEmpty()) {
            EmptyHint("该规格暂无报价")
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("最低 ${Money.format(minPrice)}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            if (minPrice != null && maxPrice != null && maxPrice != minPrice) {
                Text("最高 ${Money.format(maxPrice)} · 价差 ${Money.format(maxPrice - minPrice)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        quotes.forEach { quote ->
            val isCheapest = quote.inStock && quote.priceCents == minPrice
            val container = if (isCheapest) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(container, RoundedCornerShape(9.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlatformChip(quote.platform)
                    Text(
                        quote.shopName ?: "未知店铺",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        Money.format(quote.priceCents),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCheapest) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "评分 ${quote.shopRating ?: "-"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "销量 ${quote.salesCount ?: "-"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (quote.inStock) "有货" else "无货",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (quote.inStock) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    if (isCheapest) {
                        Text("当前最低", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.weight(1f))
                }
                LinkButton("打开链接", quote.link)
            }
        }
    }
}
