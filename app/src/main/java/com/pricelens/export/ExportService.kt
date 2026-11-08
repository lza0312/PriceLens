package com.pricelens.export

import com.pricelens.core.Money
import com.pricelens.core.TimeFmt
import com.pricelens.data.db.AppDatabase
import com.pricelens.data.db.ExportQuoteRow
import com.pricelens.domain.model.Platform

data class ExportPayload(
    val fileName: String,
    val mimeType: String,
    val content: String
)

class ExportService(private val db: AppDatabase) {

    /** CSV（UTF-8 BOM，Excel/WPS 可直接打开）。 */
    suspend fun buildCsv(): ExportPayload {
        val rows = db.quoteDao().exportRows()
        val content = buildString {
            append('\uFEFF')
            appendLine("商品,类目,规格,平台,商品标题,现价,原价,销量,店铺,店铺评分,库存,链接,来源,采集时间")
            rows.forEach { row ->
                appendLine(
                    listOf(
                        row.spuTitle,
                        row.category.orEmpty(),
                        row.skuName,
                        Platform.fromKey(row.platform).displayName,
                        row.productTitle,
                        Money.formatPlain(row.priceCents),
                        row.listPriceCents?.let { Money.formatPlain(it) }.orEmpty(),
                        row.salesCount?.toString().orEmpty(),
                        row.shopName.orEmpty(),
                        row.shopRating?.toString().orEmpty(),
                        if (row.inStock) "有货" else "无货",
                        row.link,
                        row.sourceKind,
                        TimeFmt.dateTime(row.capturedAt)
                    ).joinToString(",") { escapeCsv(it) }
                )
            }
        }
        return ExportPayload("pricelens-${TimeFmt.date(System.currentTimeMillis())}.csv", "text/csv", content)
    }

    /**
     * Excel 2003 XML（.xls）。不依赖第三方库，Excel 可直接打开。
     * 两个工作表：最新报价、价格历史。
     */
    suspend fun buildExcelXml(): ExportPayload {
        val rows = db.quoteDao().exportRows()
        val latest = rows.groupBy { it.offerKey }.mapNotNull { (_, list) ->
            list.maxByOrNull { it.capturedAt }
        }.sortedBy { it.spuTitle }

        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<?mso-application progid=\"Excel.Sheet\"?>\n")
            append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" ")
            append("xmlns:o=\"urn:schemas-microsoft-com:office:office\" ")
            append("xmlns:x=\"urn:schemas-microsoft-com:office:excel\" ")
            append("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n")
            append("<Styles><Style ss:ID=\"Header\"><Font ss:Bold=\"1\"/>")
            append("<Interior ss:Color=\"#DDEBF7\" ss:Pattern=\"Solid\"/></Style></Styles>\n")
            appendSheet(this, "最新报价", latest)
            appendSheet(this, "价格历史", rows)
            append("</Workbook>")
        }
        return ExportPayload(
            "pricelens-${TimeFmt.date(System.currentTimeMillis())}.xls",
            "application/vnd.ms-excel",
            xml
        )
    }

    private fun appendSheet(sb: StringBuilder, name: String, rows: List<ExportQuoteRow>) {
        sb.append("<Worksheet ss:Name=\"").append(escapeXml(name)).append("\"><Table>\n")
        val headers = listOf(
            "商品", "类目", "规格", "平台", "商品标题", "现价", "原价",
            "销量", "店铺", "店铺评分", "库存", "链接", "来源", "采集时间"
        )
        sb.append("<Row>")
        headers.forEach { h ->
            sb.append("<Cell ss:StyleID=\"Header\"><Data ss:Type=\"String\">")
                .append(escapeXml(h)).append("</Data></Cell>")
        }
        sb.append("</Row>\n")

        rows.forEach { row ->
            sb.append("<Row>")
            appendStringCell(sb, row.spuTitle)
            appendStringCell(sb, row.category.orEmpty())
            appendStringCell(sb, row.skuName)
            appendStringCell(sb, Platform.fromKey(row.platform).displayName)
            appendStringCell(sb, row.productTitle)
            appendNumberCell(sb, Money.formatPlain(row.priceCents))
            appendNumberCell(sb, row.listPriceCents?.let { Money.formatPlain(it) }.orEmpty())
            appendNumberCell(sb, row.salesCount?.toString().orEmpty())
            appendStringCell(sb, row.shopName.orEmpty())
            appendNumberCell(sb, row.shopRating?.toString().orEmpty())
            appendStringCell(sb, if (row.inStock) "有货" else "无货")
            appendStringCell(sb, row.link)
            appendStringCell(sb, row.sourceKind)
            appendStringCell(sb, TimeFmt.dateTime(row.capturedAt))
            sb.append("</Row>\n")
        }
        sb.append("</Table></Worksheet>\n")
    }

    private fun appendStringCell(sb: StringBuilder, value: String) {
        sb.append("<Cell><Data ss:Type=\"String\">").append(escapeXml(value)).append("</Data></Cell>")
    }

    private fun appendNumberCell(sb: StringBuilder, value: String) {
        if (value.isBlank()) {
            sb.append("<Cell/>")
        } else {
            sb.append("<Cell><Data ss:Type=\"Number\">").append(value).append("</Data></Cell>")
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
