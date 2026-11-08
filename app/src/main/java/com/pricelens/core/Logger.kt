package com.pricelens.core

import android.graphics.Bitmap
import android.util.Log
import java.io.ByteArrayOutputStream

object Logger {
    private const val TAG = "PriceLens"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun e(message: String, tr: Throwable? = null) {
        Log.e(TAG, message, tr)
    }
}

/** 平均哈希 / 差分哈希，纯 Kotlin 实现，用于可选的图片去重。 */
object ImageHash {

    fun dHash(bitmap: Bitmap, size: Int = 9): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val gray = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val p = scaled.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                gray[y * size + x] = (r * 299 + g * 587 + b * 114) / 1000
            }
        }
        val sb = StringBuilder()
        for (y in 0 until size) {
            var nibble = 0
            for (x in 0 until size - 1) {
                nibble = (nibble shl 1) or if (gray[y * size + x] > gray[y * size + x + 1]) 1 else 0
            }
            sb.append(Integer.toHexString(nibble))
        }
        if (scaled != bitmap) scaled.recycle()
        return sb.toString()
    }

    fun hammingDistance(a: String, b: String): Int {
        if (a.length != b.length) return Int.MAX_VALUE
        var diff = 0
        for (i in a.indices) {
            val x = a[i].digitToIntOrNull(16) ?: return Int.MAX_VALUE
            val y = b[i].digitToIntOrNull(16) ?: return Int.MAX_VALUE
            diff += Integer.bitCount(x xor y)
        }
        return diff
    }
}

fun Bitmap.toJpegBytes(quality: Int = 85): ByteArray {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}
