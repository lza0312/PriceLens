package com.pricelens.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.pricelens.data.db.AlertRow
import com.pricelens.data.db.WatchRow
import com.pricelens.domain.model.Platform
import com.pricelens.ui.components.BannerTone
import com.pricelens.ui.components.EmptyHint
import com.pricelens.ui.components.InfoBanner
import com.pricelens.ui.components.PlatformChip
import com.pricelens.ui.components.SectionCard
import kotlinx.coroutines.launch

@Composable
fun WatchScreen() {
    val watchRepo = AppContainer.watchRepository
    val scope = rememberCoroutineScope()

    val rows by watchRepo.observeRows().collectAsState(initial = emptyList())
    val alerts by watchRepo.observeAlerts().collectAsState(initial = emptyList())

    var message by remember { mutableStateOf<String?>(null) }
    var tone by remember { mutableStateOf(BannerTone.INFO) }
    var notes by remember { mutableStateOf<List<String>>(emptyList()) }
    var checking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WatchRow?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("关注与降价提醒", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "关注绑定到具体规格（SKU）。例如一双鞋只盯 45 码，不会因为其它尺码降价而提醒。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        message?.let { InfoBanner(it, tone = tone) }

        SectionCard("提醒规则（严格判定）") {
            Text(
                "只有同时满足以下条件才提醒：\n" +
                    "1. 该目标 SKU 当前有货；\n" +
                    "2. 当前最低有货价严格低于上一次记录的最低价。\n\n" +
                    "以下情况一律不提醒：\n" +
                    "· 出现更低价链接，但目标 SKU 缺货；\n" +
                    "· 目标 SKU 有货，但价格没变；\n" +
                    "· 首次记录（没有「上次价格」可比较）；\n" +
                    "· 设置了目标价但当前价仍未低于目标价。",
                style = MaterialTheme.typography.bodySmall
            )
        }

        SectionCard("定时检查") {
            Text(
                "使用 WorkManager 周期任务，最小间隔 15 分钟；实际执行时间由系统调度，省电模式下可能延后。" +
                    "国产 ROM 还需在系统设置里允许「自启动 / 后台运行」，否则可能长期不执行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    scope.launch {
                        checking = true
                        val report = watchRepo.checkAll()
                        message = "检查完成：关注 ${report.watchCount} 个，刷新 ${report.fetched} 个，" +
                            "跳过 ${report.skipped} 个，新提醒 ${report.alerts.size} 条"
                        tone = if (report.alerts.isNotEmpty()) BannerTone.SUCCESS else BannerTone.INFO
                        notes = report.notes.take(6)
                        checking = false
                    }
                }, enabled = !checking) {
                    Text(if (checking) "检查中…" else "立即检查一次")
                }
                if (rows.isEmpty()) {
                    Text("还没有关注", style = MaterialTheme.typography.labelSmall)
                }
            }
            notes.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionCard("降价提醒记录") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { scope.launch { watchRepo.markAlertsRead() } }) { Text("全部已读") }
                TextButton(onClick = { scope.launch { watchRepo.clearAlerts() } }) { Text("清空记录") }
            }
            if (alerts.isEmpty()) {
                EmptyHint("暂无降价提醒")
            } else {
                alerts.take(12).forEach { alert -> AlertCard(alert) }
                if (alerts.size > 12) {
                    Text("仅展示最近 12 条", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        SectionCard("我的关注（按 SKU）") {
            if (rows.isEmpty()) {
                EmptyHint("在商品详情页选择某个规格后，点「关注此规格」。")
            } else {
                rows.forEach { row ->
                    WatchCard(
                        row = row,
                        onToggle = { enabled ->
                            scope.launch { watchRepo.setEnabled(row.watchId, enabled) }
                        },
                        onEditTarget = { editing = row },
                        onDelete = {
                            scope.launch {
                                watchRepo.removeWatch(row.watchId)
                                message = "已取消关注「${row.skuName}」"
                                tone = BannerTone.INFO
                            }
                        }
                    )
                }
            }
        }
    }

    val editingRow = editing
    if (editingRow != null) {
        var input by remember(editingRow.watchId) {
            mutableStateOf(editingRow.targetPriceCents?.let { Money.formatPlain(it) } ?: "")
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("修改目标价") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("留空表示只要比上次记录更低就提醒。", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("目标价（元）") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        watchRepo.setTargetPrice(editingRow.watchId, Money.parseToCents(input))
                        editing = null
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun WatchCard(
    row: WatchRow,
    onToggle: (Boolean) -> Unit,
    onEditTarget: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                row.spuTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text("规格：${row.skuName}", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "上次记录 ${Money.format(row.lastPriceCents)}" +
                        (row.targetPriceCents?.let { " · 目标价 ${Money.format(it)}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text("${row.quoteCount} 条报价", style = MaterialTheme.typography.labelSmall)
            }
            if (row.lastCheckedAt > 0) {
                Text(
                    "最近检查：${TimeFmt.dateTime(row.lastCheckedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (row.enabled) "已启用" else "已暂停",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(checked = row.enabled, onCheckedChange = onToggle)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEditTarget) { Text("目标价") }
                TextButton(onClick = onDelete) { Text("取消关注") }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: AlertRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alert.read) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    alert.spuTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!alert.read) {
                    Text("新", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text("规格：${alert.skuName}", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${Money.format(alert.oldPriceCents)} → ${Money.format(alert.newPriceCents)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                PlatformChip(Platform.fromKey(alert.platform))
                Text(
                    alert.shopName ?: "-",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                TimeFmt.dateTime(alert.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
