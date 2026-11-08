package com.pricelens.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.pricelens.core.Money
import com.pricelens.core.TimeFmt
import com.pricelens.data.AppContainer
import com.pricelens.domain.model.Platform
import com.pricelens.domain.model.TrendPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BannerTone { INFO, WARNING, ERROR, SUCCESS }

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun InfoBanner(text: String, tone: BannerTone = BannerTone.INFO, modifier: Modifier = Modifier) {
    val container = when (tone) {
        BannerTone.INFO -> MaterialTheme.colorScheme.primaryContainer
        BannerTone.WARNING -> MaterialTheme.colorScheme.secondaryContainer
        BannerTone.ERROR -> MaterialTheme.colorScheme.errorContainer
        BannerTone.SUCCESS -> MaterialTheme.colorScheme.secondaryContainer
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PlatformChip(platform: Platform, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(50)
            )
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            platform.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MoneyText(cents: Long?, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    Text(
        Money.format(cents),
        modifier = modifier,
        style = if (emphasize) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
        fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
        color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 26.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LinkButton(text: String, url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (url.isBlank()) return
    OutlinedButton(
        modifier = modifier,
        onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    ) { Text(text) }
}

/**
 * 价格趋势折线图（纯 Canvas，无第三方依赖）。
 */
@Composable
fun PriceTrendChart(points: List<TrendPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) {
        EmptyHint("暂无价格历史", modifier)
        return
    }
    val minPrice = points.minOf { it.priceCents }
    val maxPrice = points.maxOf { it.priceCents }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val latest = points.last()

    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        val padLeft = 54.dp.toPx()
        val padRight = 12.dp.toPx()
        val padTop = 14.dp.toPx()
        val padBottom = 30.dp.toPx()
        val plotW = size.width - padLeft - padRight
        val plotH = size.height - padTop - padBottom

        for (i in 0..3) {
            val y = padTop + plotH * i / 3f
            drawLine(gridColor, Offset(padLeft, y), Offset(padLeft + plotW, y), strokeWidth = 1f)
        }

        fun xOf(index: Int): Float =
            if (points.size == 1) padLeft + plotW / 2f
            else padLeft + plotW * index / (points.size - 1).toFloat()

        fun yOf(value: Long): Float {
            val span = (maxPrice - minPrice).coerceAtLeast(1L)
            return padTop + plotH * (1f - (value - minPrice).toFloat() / span)
        }

        if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = xOf(index)
                val y = yOf(point.priceCents)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx()))
        }

        points.forEachIndexed { index, point ->
            drawCircle(lineColor, radius = 3.5.dp.toPx(), center = Offset(xOf(index), yOf(point.priceCents)))
        }

        val paint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.apply {
            drawText(Money.format(maxPrice), 4.dp.toPx(), padTop + 12.sp.toPx(), paint)
            drawText(Money.format(minPrice), 4.dp.toPx(), padTop + plotH, paint)
            drawText(TimeFmt.date(points.first().timestamp), padLeft, size.height - 8.dp.toPx(), paint)
            drawText(
                TimeFmt.date(points.last().timestamp),
                padLeft + plotW - 60.dp.toPx(),
                size.height - 8.dp.toPx(),
                paint
            )
        }
    }
    Text(
        "最新：${Money.format(latest.priceCents)} · ${latest.platform.displayName} · ${TimeFmt.dateTime(latest.timestamp)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 导出按钮（SAF，不需要存储权限）。
 */
@Composable
fun ExportButtons(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    fun writeTo(uri: Uri, build: suspend () -> com.pricelens.export.ExportPayload) {
        scope.launch {
            runCatching {
                val payload = build()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(payload.content.toByteArray(Charsets.UTF_8))
                    }
                }
                message = "已导出 ${payload.fileName}"
            }.onFailure { message = "导出失败：${it.message}" }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) writeTo(uri) { AppContainer.exportService.buildCsv() }
    }

    val excelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.ms-excel")
    ) { uri ->
        if (uri != null) writeTo(uri) { AppContainer.exportService.buildExcelXml() }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { csvLauncher.launch("pricelens-${TimeFmt.date(System.currentTimeMillis())}.csv") }) {
                Text("导出 CSV")
            }
            OutlinedButton(onClick = { excelLauncher.launch("pricelens-${TimeFmt.date(System.currentTimeMillis())}.xls") }) {
                Text("导出 Excel")
            }
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PlatformRiskBanner(modifier: Modifier = Modifier) {
    InfoBanner(
        text = "采集功能会请求各平台公开页面，可能触及平台用户协议与账号风控。" +
            "请自行承担使用风险；本工具不上传数据、不做账号模拟登录。" +
            "适配器不可用时只如实报错，不会生成任何虚假报价。",
        tone = BannerTone.WARNING,
        modifier = modifier
    )
}
