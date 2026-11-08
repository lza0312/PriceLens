package com.pricelens.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 金额统一用「分」存储，避免浮点误差。 */
object Money {

    fun format(cents: Long?): String {
        if (cents == null) return "-"
        val yuan = cents / 100.0
        return if (cents % 100 == 0L) {
            "¥" + cents.div(100).toString()
        } else {
            "¥" + String.format(Locale.CHINA, "%.2f", yuan)
        }
    }

    fun formatPlain(cents: Long?): String {
        if (cents == null) return ""
        return String.format(Locale.CHINA, "%.2f", cents / 100.0)
    }

    /**
     * 解析价格文本，返回「分」。支持 12.5 / 12.50元 / ¥1,299.00 / 1299-2599（取最低）。
     * 解析失败返回 null。
     */
    fun parseToCents(raw: String?): Long? {
        if (raw == null) return null
        val text = raw.trim()
            .replace("￥", "")
            .replace("¥", "")
            .replace("元", "")
            .replace(",", "")
            .replace("，", "")
            .replace(" ", "")
        if (text.isEmpty()) return null
        val first = text.split("-", "~", "至").firstOrNull()?.trim().orEmpty()
        val cleaned = Regex("[^0-9.]").replace(first, "")
        if (cleaned.isEmpty() || cleaned == ".") return null
        val value = cleaned.toDoubleOrNull() ?: return null
        if (value <= 0.0) return null
        return (value * 100).toLong()
    }

    /** 价格区间桶，用于去重时避免把明显不同价位的商品合并。 */
    fun priceBucket(cents: Long, ratio: Double = 0.08): Long {
        if (cents <= 0) return 0
        val step = (cents * ratio).toLong().coerceAtLeast(100L)
        return (cents / step) * step
    }
}

object TimeFmt {
    private val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val dateOnly = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

    fun dateTime(ts: Long): String = dateTime.format(Date(ts))
    fun date(ts: Long): String = dateOnly.format(Date(ts))
    fun time(ts: Long): String = clock.format(Date(ts))
}
