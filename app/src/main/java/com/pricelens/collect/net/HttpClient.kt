package com.pricelens.collect.net

import com.pricelens.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 采集层的网络入口。
 * 说明：App 内请求与桌面浏览器请求的出口 IP 相同，但平台仍可能对 App UA / 无 Cookie 请求做风控。
 */
class HttpClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    private val mobileUa =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    suspend fun getText(
        url: String,
        referer: String? = null,
        mobile: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", if (mobile) mobileUa else desktopUa)
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8"
                )
                .header("Accept-Language", "zh-CN,zh;q=0.9")
            if (referer != null) builder.header("Referer", referer)
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.d("HTTP ${response.code} $url")
                    return@use null
                }
                val body = response.body?.string()
                Logger.d("HTTP ${response.code} $url len=${body?.length ?: 0}")
                body
            }
        } catch (e: Exception) {
            Logger.e("HTTP request failed: $url", e)
            null
        }
    }

    /** 解析短链跳转后的最终地址（例如 u.jd.com 短链）。用 GET 以兼容只对 GET 生效的跳转。 */
    suspend fun resolveFinalUrl(url: String): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", mobileUa)
                .build()
            client.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (e: Exception) {
            Logger.e("resolveFinalUrl failed: $url", e)
            url
        }
    }
}
