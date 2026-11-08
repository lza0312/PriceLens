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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.pricelens.collect.AdapterInfo
import com.pricelens.collect.AdapterState
import com.pricelens.data.AppContainer
import com.pricelens.data.prefs.AppSettings
import com.pricelens.ui.components.BannerTone
import com.pricelens.ui.components.ExportButtons
import com.pricelens.ui.components.InfoBanner
import com.pricelens.ui.components.PlatformRiskBanner
import com.pricelens.ui.components.SectionCard
import com.pricelens.work.WatchScheduler
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AdapterStatusScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = AppContainer.settingsStore
    val settings by settingsStore.settings.collectAsState(initial = AppSettings())
    val infos = remember { AppContainer.registry.infos() }

    var message by remember { mutableStateOf<String?>(null) }

    var weightPrice by remember { mutableStateOf(settings.weightPrice.toFloat()) }
    var weightRating by remember { mutableStateOf(settings.weightRating.toFloat()) }
    var weightSales by remember { mutableStateOf(settings.weightSales.toFloat()) }
    var intervalHours by remember { mutableStateOf(settings.checkIntervalHours.toFloat()) }

    LaunchedEffect(settings.weightPrice, settings.weightRating, settings.weightSales) {
        weightPrice = settings.weightPrice.toFloat()
        weightRating = settings.weightRating.toFloat()
        weightSales = settings.weightSales.toFloat()
    }
    LaunchedEffect(settings.checkIntervalHours) {
        intervalHours = settings.checkIntervalHours.toFloat()
    }

    val weightSum = (weightPrice + weightRating + weightSales).takeIf { it > 0f } ?: 1f
    val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("适配器与设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "每个平台一个独立实现，主流程只依赖统一接口。不可用时如实报错，不返回任何伪造报价。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PlatformRiskBanner()

        message?.let { InfoBanner(it, tone = BannerTone.SUCCESS) }

        SectionCard("平台适配器状态") {
            infos.forEach { info -> AdapterCard(info) }
        }

        SectionCard("通知与后台") {
            InfoBanner(
                text = if (notificationsOn) {
                    "系统通知权限已开启。"
                } else {
                    "系统通知权限被关闭，降价提醒不会弹出；请到「设置 → 应用 → PriceLens → 通知」开启。"
                },
                tone = if (notificationsOn) BannerTone.SUCCESS else BannerTone.WARNING
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("发送降价通知", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "只发送满足严格判定条件的提醒",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.notifyEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsStore.setNotifyEnabled(enabled) }
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("仅在低于目标价时提醒", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "关闭时：只要比上次最低有货价更低就提醒",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.alertOnlyBelowTarget,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsStore.setAlertOnlyBelowTarget(enabled) }
                    }
                )
            }
            Text(
                "检查间隔：${intervalHours.toInt()} 小时（WorkManager 最小 15 分钟，系统可能延后）",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = intervalHours,
                onValueChange = { intervalHours = it },
                valueRange = 1f..24f,
                steps = 22,
                onValueChangeFinished = {
                    val hours = intervalHours.toInt().coerceIn(1, 24)
                    scope.launch { settingsStore.setCheckIntervalHours(hours) }
                    WatchScheduler.ensurePeriodic(context, hours)
                }
            )
            Text(
                "Android 后台限制：WorkManager 周期任务受 Doze / 省电策略影响；" +
                    "国产 ROM 需在系统设置中允许自启动与后台运行，" +
                    "并在电池优化中把本应用设为「不优化」，否则定时检查可能长期不执行。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard("性价比评分权重") {
            WeightSlider("价格", weightPrice, weightSum) { weightPrice = it }
            WeightSlider("店铺评分", weightRating, weightSum) { weightRating = it }
            WeightSlider("销量", weightSales, weightSum) { weightSales = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        settingsStore.setWeights(
                            weightPrice.toDouble(),
                            weightRating.toDouble(),
                            weightSales.toDouble()
                        )
                        message = "评分权重已保存（三项会自动归一化）"
                    }
                }) { Text("保存权重") }
                Text(
                    "用于商品详情页的「性价比推荐」排序",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }

        SectionCard("去重与清洗") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("严格去重（6 小时窗口）", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "关闭后仅跳过 1 小时内同链接同价格记录；标题归一化与相似度匹配始终启用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.dedupeStrict,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsStore.setDedupeStrict(enabled) }
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("图像哈希去重（预留）", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "当前未接入采集流程，打开也不会影响判定；等图片字段稳定后再启用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.imageHashEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsStore.setImageHashEnabled(enabled) }
                    }
                )
            }
        }

        SectionCard("导出") {
            Text(
                "CSV（UTF-8 BOM，Excel 可直接打开）与 Excel 2003 XML（两个工作表：最新报价 / 价格历史）。" +
                    "通过系统文件选择器保存，不需要存储权限。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ExportButtons()
        }
    }
}

@Composable
private fun WeightSlider(label: String, value: Float, sum: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            String.format(Locale.CHINA, "%.0f%%", value / sum * 100f),
            style = MaterialTheme.typography.labelMedium
        )
    }
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = 0f..1f
    )
}

@Composable
private fun AdapterCard(info: AdapterInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    info.platform.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    info.state.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (info.state) {
                        AdapterState.AVAILABLE -> MaterialTheme.colorScheme.primary
                        AdapterState.PARTIAL -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }
            Text(info.stateMessage, style = MaterialTheme.typography.bodySmall)
            Text(
                "能力：" + capabilityText(info),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "权限：" + info.requiredPermissions,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "失效风险：" + info.riskNotes,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun capabilityText(info: AdapterInfo): String {
    val caps = buildList {
        if (info.capability.linkFetch) add("链接查询")
        if (info.capability.keywordSearch) add("关键词搜索")
        if (info.capability.categorySearch) add("类目搜索")
        if (info.capability.shopSearch) add("店铺搜索")
    }
    return caps.ifEmpty { listOf("无可用能力") }.joinToString(" / ")
}
