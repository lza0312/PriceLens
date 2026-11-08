package com.pricelens.collect.importing

import com.pricelens.core.Money
import com.pricelens.domain.model.OfferSnapshot
import com.pricelens.domain.model.Platform
import com.pricelens.domain.model.SourceKind

data class CsvParseResult(
    val offers: List<OfferSnapshot>,
    val errors: List<String>
)

/**
 * CSV 导入：表头可识别中英文同义词，缺少表头直接报错。
 * 支持逗号、制表符、分号分隔；支持双引号包裹字段。
 */
object CsvImporter {

    private val aliases = mapOf(
        "platform" to listOf("platform", "平台", "渠道", "平台名称"),
        "title" to listOf("title", "name", "商品名称", "商品", "标题", "name_cn"),
        "spec" to listOf("spec", "sku", "规格", "型号", "规格型号", "尺码"),
        "price" to listOf("price", "价格", "现价", "售价", "到手价", "current_price"),
        "listPrice" to listOf("listprice", "原价", "划线价", "市场价"),
        "sales" to listOf("sales", "销量", "已售", "销售量"),
        "shop" to listOf("shop", "店铺", "店铺名称", "商家", "店铺名"),
        "rating" to listOf("rating", "评分", "店铺评分", "dsr"),
        "link" to listOf("link", "url", "链接", "商品链接", "地址"),
        "stock" to listOf("stock", "库存", "有货", "in_stock", "inStock"),
        "category" to listOf("category", "类目", "分类", "类别")
    )

    fun parse(raw: String): CsvParseResult {
        val lines = raw.replace("\r\n", "\n").replace("\r", "\n")
            .split("\n")
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return CsvParseResult(emptyList(), listOf("内容为空"))

        val delimiter = detectDelimiter(lines.first())
        val header = splitLine(lines.first(), delimiter)
        val columnIndex = resolveHeader(header)
        if (!columnIndex.containsKey("title") || !columnIndex.containsKey("price")) {
            return CsvParseResult(
                emptyList(),
                listOf("缺少必需表头：商品名称(title) 和 价格(price)。当前表头：${header.joinToString(" | ")}")
            )
        }

        val errors = mutableListOf<String>()
        val offers = mutableListOf<OfferSnapshot>()

        lines.drop(1).forEachIndexed { index, line ->
            val cells = splitLine(line, delimiter)
            val lineNo = index + 2
            val title = cells.getOrNull(columnIndex["title"] ?: -1)?.trim().orEmpty()
            val priceRaw = cells.getOrNull(columnIndex["price"] ?: -1)?.trim().orEmpty()
            val price = Money.parseToCents(priceRaw)

            if (title.isEmpty()) {
                errors += "第 $lineNo 行：商品名称为空，已跳过"
                return@forEachIndexed
            }
            if (price == null) {
                errors += "第 $lineNo 行：价格「$priceRaw」无法解析，已跳过"
                return@forEachIndexed
            }

            val platformRaw = cells.getOrNull(columnIndex["platform"] ?: -1)?.trim().orEmpty()
            val link = cells.getOrNull(columnIndex["link"] ?: -1)?.trim().orEmpty()
            val platform = resolvePlatform(platformRaw, link)

            offers += OfferSnapshot(
                platform = platform,
                productTitle = title,
                skuLabel = cells.getOrNull(columnIndex["spec"] ?: -1)?.trim()?.takeIf { it.isNotBlank() },
                link = link,
                priceCents = price,
                listPriceCents = Money.parseToCents(cells.getOrNull(columnIndex["listPrice"] ?: -1)),
                salesCount = cells.getOrNull(columnIndex["sales"] ?: -1)?.let { parseCount(it) },
                shopName = cells.getOrNull(columnIndex["shop"] ?: -1)?.trim()?.takeIf { it.isNotBlank() },
                shopRating = cells.getOrNull(columnIndex["rating"] ?: -1)?.trim()?.toDoubleOrNull()
                    ?: cells.getOrNull(columnIndex["rating"] ?: -1)?.trim()?.removeSuffix("分")?.toDoubleOrNull(),
                inStock = parseStock(cells.getOrNull(columnIndex["stock"] ?: -1)),
                categoryHint = cells.getOrNull(columnIndex["category"] ?: -1)?.trim()?.takeIf { it.isNotBlank() },
                sourceKind = SourceKind.CSV
            )
        }

        return CsvParseResult(offers, errors)
    }

    private fun detectDelimiter(headerLine: String): Char {
        val candidates = listOf(',', '\t', ';')
        return candidates.maxByOrNull { c -> headerLine.count { it == c } } ?: ','
    }

    private fun splitLine(line: String, delimiter: Char): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    cells.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        cells.add(current.toString())
        return cells
    }

    private fun resolveHeader(header: List<String>): Map<String, Int> {
        val normalized = header.map { it.trim().lowercase().replace(" ", "").replace("_", "") }
        val result = mutableMapOf<String, Int>()
        aliases.forEach { (key, names) ->
            val idx = normalized.indexOfFirst { cell -> names.any { it.lowercase().replace("_", "") == cell } }
            if (idx >= 0) result[key] = idx
        }
        return result
    }

    private fun resolvePlatform(raw: String, link: String): Platform {
        if (raw.isNotBlank()) {
            val key = raw.trim().lowercase()
            Platform.entries.firstOrNull { it.key == key || it.displayName == raw.trim() || it.name.lowercase() == key }
                ?.let { return it }
        }
        return com.pricelens.collect.linking.ShareLinkParser.detectPlatform(link) ?: Platform.MANUAL
    }

    private fun parseStock(raw: String?): Boolean {
        val text = raw?.trim()?.lowercase().orEmpty()
        if (text.isEmpty()) return true
        return when {
            text.contains("无货") || text.contains("缺货") || text.contains("售罄") ||
                text == "0" || text == "false" || text == "no" -> false
            else -> true
        }
    }

    private fun parseCount(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val cleaned = text.replace(",", "").replace("+", "")
        if (cleaned.isEmpty()) return null
        return cleaned.toLongOrNull()
    }

    fun template(): String = buildString {
        appendLine("平台,商品名称,规格,价格,原价,销量,店铺,店铺评分,链接,库存,类目")
        appendLine("京东,示例商品 256GB 黑色,256GB 黑色,1999.00,2199.00,12000,示例旗舰店,4.9,https://item.jd.com/100000000000.html,有货,手机")
    }
}
