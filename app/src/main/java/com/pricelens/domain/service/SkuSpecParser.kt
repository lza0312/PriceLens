package com.pricelens.domain.service

import com.pricelens.core.TextNormalizer
import com.pricelens.domain.model.ParsedSpec

/**
 * 规格解析：从平台给的规格文本或标题里提取可比较的维度。
 * 解析不到维度时，用整段文本作为规格键（不同文本 = 不同 SKU）。
 */
object SkuSpecParser {

    private val storageRegex =
        Regex("(?i)(\\d{1,4})\\s*(gb|tb|t|g)(?![a-z])")
    private val ramRomRegex =
        Regex("(?i)(\\d{1,3})\\s*[\\+＋]\\s*(\\d{2,4})\\s*(gb|tb|t|g)(?![a-z])")
    private val shoeSizeRegex = Regex("(\\d{2}(?:\\.5)?)\\s*码")
    private val clothSizeRegex = Regex("(?i)(?<![a-z])(xs|xxl|xxxl|xl|s|m|l)(?![a-z])")
    private val colorRegex =
        Regex("(曜石黑|钛黑|深空灰|星光色|午夜蓝|远峰蓝|原色钛|黑色|白色|银色|灰色|金色|蓝色|绿色|紫色|粉色|红色|钛色|灰|黑|白|银|金|蓝|绿|紫|粉|红)")

    private val ignoreSpec = setOf("默认", "默认规格", "请选择", "宝贝详情", "商品详情", "无")

    fun parse(rawSpec: String?): ParsedSpec {
        val source = rawSpec?.trim().orEmpty()
        if (source.isEmpty()) return ParsedSpec("默认规格", "default", emptyMap())

        val attrs = linkedMapOf<String, String>()

        ramRomRegex.find(source)?.let { match ->
            val ram = match.groupValues[1]
            val rom = normalizeStorage(match.groupValues[2], match.groupValues[3])
            attrs["memory"] = "$ram+$rom"
        } ?: storageRegex.find(source)?.let { match ->
            attrs["storage"] = normalizeStorage(match.groupValues[1], match.groupValues[2])
        }

        shoeSizeRegex.find(source)?.let { attrs["shoeSize"] = it.groupValues[1] + "码" }

        val color = colorRegex.find(source)?.value
        if (color != null) attrs["color"] = color

        if (!attrs.containsKey("shoeSize")) {
            clothSizeRegex.find(source)?.let { attrs["size"] = it.groupValues[1].uppercase() }
        }

        if (attrs.isEmpty()) {
            val normalized = TextNormalizer.normalize(source).ifBlank { "default" }
            if (normalized in ignoreSpec) return ParsedSpec("默认规格", "default", emptyMap())
            return ParsedSpec(source, "raw:$normalized", mapOf("spec" to source))
        }

        val label = buildString {
            attrs["memory"]?.let { append(it).append(' ') }
            attrs["storage"]?.let { append(it).append(' ') }
            attrs["shoeSize"]?.let { append(it).append(' ') }
            attrs["size"]?.let { append(it).append(' ') }
            attrs["color"]?.let { append(it) }
        }.trim().ifEmpty { source }

        val normKey = attrs.entries.sortedBy { it.key }.joinToString("|") { "${it.key}=${it.value}" }
        return ParsedSpec(label, normKey, attrs)
    }

    private fun normalizeStorage(number: String, unit: String): String {
        val u = unit.uppercase()
        return if (u == "T" || u == "TB") "${number}TB" else "${number}GB"
    }

    /** 用于「同款聚合」时判断两个规格键是否同一维度。 */
    fun isSameDimension(a: String, b: String): Boolean {
        if (a == b) return true
        val attrsA = a.split("|").filter { it.contains("=") }.toSet()
        val attrsB = b.split("|").filter { it.contains("=") }.toSet()
        return attrsA.isNotEmpty() && attrsA.intersect(attrsB).isNotEmpty()
    }
}
