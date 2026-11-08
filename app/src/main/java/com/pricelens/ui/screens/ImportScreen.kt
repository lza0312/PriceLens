package com.pricelens.ui.screens

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.pricelens.collect.AdapterUnavailableException
import com.pricelens.collect.importing.CsvImporter
import com.pricelens.collect.linking.ParsedShare
import com.pricelens.collect.linking.ShareLinkParser
import com.pricelens.core.Money
import com.pricelens.core.ShareInbox
import com.pricelens.data.AppContainer
import com.pricelens.domain.model.OfferSnapshot
import com.pricelens.domain.model.Platform
import com.pricelens.domain.model.SourceKind
import com.pricelens.ui.components.BannerTone
import com.pricelens.ui.components.InfoBanner
import com.pricelens.ui.components.PlatformChip
import com.pricelens.ui.components.PlatformRiskBanner
import com.pricelens.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ImportTab(val label: String) {
    SHARE("分享链接"),
    CLIPBOARD("剪贴板"),
    CSV("CSV 导入"),
    MANUAL("手动补录")
}

@Composable
fun ImportScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = AppContainer.priceRepository
    val registry = AppContainer.registry

    var tab by remember { mutableStateOf(ImportTab.SHARE) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageTone by remember { mutableStateOf(BannerTone.INFO) }

    var shareText by remember { mutableStateOf("") }
    var csvText by remember { mutableStateOf("") }
    val inboxText by ShareInbox.text.collectAsState()

    LaunchedEffect(inboxText) {
        val incoming = inboxText
        if (incoming.isNullOrBlank()) return@LaunchedEffect
        shareText = incoming
        tab = ImportTab.SHARE
        message = "已接收其他 App 分享的文本，请确认后查询"
        messageTone = BannerTone.INFO
        ShareInbox.consume()
    }

    val parsedShares = remember(shareText) { ShareLinkParser.parse(shareText) }
    val csvResult = remember(csvText) {
        if (csvText.isBlank()) null else CsvImporter.parse(csvText)
    }

    fun fetchShare(parsed: ParsedShare) {
        val url = parsed.url ?: return
        val platform = parsed.platform
        val adapter = platform?.let { registry.get(it) }
        if (adapter == null || !adapter.capability.linkFetch) {
            message = "${platform?.displayName ?: "该平台"} 适配器未可用：" +
                (adapter?.stateMessage ?: "平台未识别或未注册，请手动补录")
            messageTone = BannerTone.ERROR
            return
        }
        scope.launch {
            busy = true
            message = "正在查询 ${platform.displayName} 商品页…"
            messageTone = BannerTone.INFO
            try {
                var target = url
                if (ShareLinkParser.isShortLink(target)) {
                    target = AppContainer.httpClient.resolveFinalUrl(target)
                }
                val snapshot = adapter.fetchByLink(target)
                val outcome = repo.importOffers(
                    offers = listOf(snapshot.copy(sourceKind = SourceKind.SHARE_LINK))
                )
                message = if (outcome.quotesInserted > 0) {
                    "已导入「${snapshot.productTitle.take(24)}」${Money.format(snapshot.priceCents)}" +
                        "，新增 ${outcome.spusCreated} 个商品 / ${outcome.skusCreated} 个规格"
                } else {
                    "未写入新报价（可能 6 小时内同价重复）"
                }
                messageTone = if (outcome.quotesInserted > 0) BannerTone.SUCCESS else BannerTone.WARNING
            } catch (e: AdapterUnavailableException) {
                message = "${platform.displayName} 适配器未可用：${e.message}"
                messageTone = BannerTone.ERROR
            } catch (e: Exception) {
                message = "${platform.displayName} 查询失败：${e.message ?: e::class.java.simpleName}"
                messageTone = BannerTone.ERROR
            } finally {
                busy = false
            }
        }
    }

    var manualPlatform by remember { mutableStateOf(Platform.JD) }
    var manualTitle by remember { mutableStateOf("") }
    var manualSpec by remember { mutableStateOf("") }
    var manualPrice by remember { mutableStateOf("") }
    var manualListPrice by remember { mutableStateOf("") }
    var manualSales by remember { mutableStateOf("") }
    var manualShop by remember { mutableStateOf("") }
    var manualRating by remember { mutableStateOf("") }
    var manualLink by remember { mutableStateOf("") }
    var manualCategory by remember { mutableStateOf("") }
    var manualInStock by remember { mutableStateOf(true) }

    fun submitManual() {
        val title = manualTitle.trim()
        if (title.isEmpty()) {
            message = "请填写商品名称"
            messageTone = BannerTone.WARNING
            return
        }
        val price = Money.parseToCents(manualPrice)
        if (price == null) {
            message = "价格无法解析，请填写例如 1999 或 1999.00"
            messageTone = BannerTone.WARNING
            return
        }
        val offer = OfferSnapshot(
            platform = manualPlatform,
            productTitle = title,
            skuLabel = manualSpec.trim().takeIf { it.isNotEmpty() },
            link = manualLink.trim(),
            priceCents = price,
            listPriceCents = Money.parseToCents(manualListPrice),
            salesCount = manualSales.trim().toLongOrNull(),
            shopName = manualShop.trim().takeIf { it.isNotEmpty() },
            shopRating = manualRating.trim().toDoubleOrNull(),
            inStock = manualInStock,
            categoryHint = manualCategory.trim().takeIf { it.isNotEmpty() },
            sourceKind = SourceKind.MANUAL
        )
        scope.launch {
            busy = true
            val outcome = repo.importOffers(listOf(offer))
            message = "已保存手动报价：新增 ${outcome.quotesInserted} 条" +
                (if (outcome.duplicatesSkipped > 0) "，跳过 ${outcome.duplicatesSkipped} 条重复" else "")
            messageTone = if (outcome.quotesInserted > 0) BannerTone.SUCCESS else BannerTone.WARNING
            if (outcome.quotesInserted > 0) {
                manualTitle = ""
                manualSpec = ""
                manualPrice = ""
                manualListPrice = ""
                manualSales = ""
                manualLink = ""
            }
            busy = false
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    }
                } ?: error("无法打开所选文件")
            }.onSuccess {
                csvText = it
                tab = ImportTab.CSV
                message = "已读取 CSV 文件"
                messageTone = BannerTone.INFO
            }.onFailure {
                message = "读取失败：${it.message}（请确认是 UTF-8 编码的文本 CSV）"
                messageTone = BannerTone.ERROR
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("采集入口", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "先提供不依赖平台风控的入口：分享链接解析、剪贴板、CSV、手动补录。适配器可用时才联网查询。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PlatformRiskBanner()

        message?.let { InfoBanner(it, tone = messageTone) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImportTab.entries.forEach { item ->
                FilterChip(
                    selected = tab == item,
                    onClick = {
                        tab = item
                        message = null
                    },
                    label = { Text(item.label) }
                )
            }
        }

        when (tab) {
            ImportTab.SHARE -> SectionCard("分享链接 / 分享文案") {
                OutlinedTextField(
                    value = shareText,
                    onValueChange = { shareText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("粘贴商品链接或分享文案") },
                    minLines = 3,
                    maxLines = 8
                )
                Text(
                    "纯离线解析，不会自动登录。京东链接可查询公开商品页；其余平台适配器不可用时会如实提示。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (parsedShares.isNotEmpty()) {
                    parsedShares.forEach { parsed -> ShareCard(parsed, busy) { fetchShare(it) } }
                }
            }

            ImportTab.CLIPBOARD -> SectionCard("从剪贴板采集") {
                Text(
                    "读取剪贴板文本后会切到「分享链接」进行解析；本应用不读取剪贴板以外的内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        val clipText = clipboard?.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                        if (clipText.isNullOrBlank()) {
                            message = "剪贴板为空，或系统限制读取"
                            messageTone = BannerTone.WARNING
                        } else {
                            shareText = clipText
                            tab = ImportTab.SHARE
                            message = "已读取剪贴板内容，请确认后查询"
                            messageTone = BannerTone.INFO
                        }
                    },
                    enabled = !busy
                ) { Text("读取剪贴板并解析") }
            }

            ImportTab.CSV -> SectionCard("CSV 批量导入") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { csvLauncher.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel", "application/octet-stream")) }) {
                        Text("选择 CSV 文件")
                    }
                    OutlinedButton(onClick = { csvText = CsvImporter.template() }) {
                        Text("填入模板")
                    }
                }
                OutlinedTextField(
                    value = csvText,
                    onValueChange = { csvText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("CSV 内容（UTF-8）") },
                    minLines = 5,
                    maxLines = 14
                )
                Text(
                    "必需表头：商品名称（title）与价格（price）。可选：平台、规格、原价、销量、店铺、店铺评分、链接、库存、类目。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (csvResult != null) {
                    Text(
                        "可导入 ${csvResult.offers.size} 行" +
                            if (csvResult.errors.isNotEmpty()) "，${csvResult.errors.size} 行有问题" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    csvResult.errors.take(6).forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        enabled = !busy && csvResult.offers.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                busy = true
                                val outcome = repo.importOffers(csvResult.offers)
                                message = "CSV 导入完成：新增 ${outcome.quotesInserted} 条报价，" +
                                    "跳过 ${outcome.duplicatesSkipped} 条，新增 ${outcome.spusCreated} 个商品 / " +
                                    "${outcome.skusCreated} 个规格"
                                messageTone = if (outcome.quotesInserted > 0) BannerTone.SUCCESS else BannerTone.WARNING
                                busy = false
                            }
                        }
                    ) { Text("导入 ${csvResult.offers.size} 行") }
                }
            }

            ImportTab.MANUAL -> SectionCard("手动补录") {
                Text(
                    "平台适配器不可用时的兜底入口。所有字段除名称与价格外均可留空。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Platform.entries, key = { it.key }) { platform ->
                        FilterChip(
                            selected = manualPlatform == platform,
                            onClick = { manualPlatform = platform },
                            label = { Text(platform.displayName) }
                        )
                    }
                }
                OutlinedTextField(
                    value = manualTitle,
                    onValueChange = { manualTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("商品名称（必填）") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = manualSpec,
                    onValueChange = { manualSpec = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("规格，例如 256GB 黑色 / 45 码") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualPrice,
                        onValueChange = { manualPrice = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("现价（元，必填）") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = manualListPrice,
                        onValueChange = { manualListPrice = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("原价（元）") },
                        singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualShop,
                        onValueChange = { manualShop = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("店铺名") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = manualRating,
                        onValueChange = { manualRating = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("店铺评分") },
                        singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualSales,
                        onValueChange = { manualSales = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("销量") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = manualCategory,
                        onValueChange = { manualCategory = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("类目") },
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = manualLink,
                    onValueChange = { manualLink = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("商品链接（可选）") },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("当前有货", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = manualInStock, onCheckedChange = { manualInStock = it })
                }
                Button(onClick = { submitManual() }, enabled = !busy) { Text("保存报价") }
            }
        }
    }
}

@Composable
private fun ShareCard(
    parsed: ParsedShare,
    busy: Boolean,
    onFetch: (ParsedShare) -> Unit
) {
    val context = LocalContext.current
    val adapter = parsed.platform?.let { AppContainer.registry.get(it) }
    val canFetch = adapter?.capability?.linkFetch == true

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            parsed.platform?.let { PlatformChip(it) }
            Text(
                parsed.url ?: "未解析出可访问链接",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        parsed.warning?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (parsed.url != null) {
                Button(
                    enabled = !busy && canFetch,
                    onClick = { onFetch(parsed) }
                ) { Text(if (canFetch) "查询并导入" else "适配器未可用") }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(parsed.url)))
                        }
                    }
                ) { Text("浏览器打开") }
            }
            if (canFetch.not()) {
                Text(
                    adapter?.stateMessage ?: "平台未识别，请在「手动补录」里填写",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
