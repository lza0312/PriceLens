package com.pricelens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pricelens.core.Money
import com.pricelens.core.TimeFmt
import com.pricelens.data.AppContainer
import com.pricelens.domain.model.SpuSummaryModel
import com.pricelens.ui.components.EmptyHint
import com.pricelens.ui.components.ExportButtons
import com.pricelens.ui.components.MoneyText
import com.pricelens.ui.components.PlatformChip

@Composable
fun ProductsScreen(
    onOpenSearch: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenProduct: (Long) -> Unit,
    onOpenCompare: (Long) -> Unit
) {
    val repo = AppContainer.priceRepository
    val summaries by repo.observeSummaries().collectAsState(initial = emptyList())
    val unread by AppContainer.watchRepository.observeUnreadAlerts().collectAsState(initial = 0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("PriceLens", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "本地价格采集与对比 · 纯离线存储",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (unread > 0) {
                    Badge { Text("$unread") }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                    .clickable(onClick = onOpenSearch)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "搜索商品 / 类目 / 店铺（三个入口分开）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onOpenImport) { Text("＋ 采集商品") }
                TextButton(onClick = { }) { Text("共 ${summaries.size} 款商品") }
            }
        }

        item { ExportButtons() }

        if (summaries.isEmpty()) {
            item {
                EmptyHint("还没有数据。去「采集」页添加分享链接、CSV 或手动补录，或在「搜索」页尝试适配器。")
            }
        }

        items(summaries, key = { it.spuId }) { spu ->
            ProductCard(
                spu = spu,
                onClick = { onOpenProduct(spu.spuId) },
                onCompare = { onOpenCompare(spu.spuId) }
            )
        }
    }
}

@Composable
private fun ProductCard(spu: SpuSummaryModel, onClick: () -> Unit, onCompare: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                spu.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.Bottom) {
                MoneyText(spu.minPriceCents, emphasize = true)
                if (spu.maxPriceCents != null && spu.maxPriceCents != spu.minPriceCents) {
                    Text(
                        "  ~  ${Money.format(spu.maxPriceCents)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${spu.skuCount} 规格 · ${spu.quoteCount} 条报价",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                spu.bestPlatform?.let { PlatformChip(it) }
                Text(
                    spu.bestShop ?: "未知店铺",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCompare) { Text("横向对比") }
            }

            if (spu.lastCapturedAt > 0) {
                Text(
                    "最近采集：${TimeFmt.dateTime(spu.lastCapturedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
