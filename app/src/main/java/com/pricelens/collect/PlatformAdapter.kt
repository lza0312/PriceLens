package com.pricelens.collect

import com.pricelens.domain.model.OfferSnapshot
import com.pricelens.domain.model.Platform

enum class AdapterState(val label: String) {
    AVAILABLE("可用"),
    PARTIAL("部分可用"),
    NEEDS_LOGIN("需登录"),
    NOT_IMPLEMENTED("未实现"),
    BROKEN("已失效")
}

data class AdapterCapability(
    val keywordSearch: Boolean = false,
    val categorySearch: Boolean = false,
    val shopSearch: Boolean = false,
    val linkFetch: Boolean = false
)

data class AdapterInfo(
    val platform: Platform,
    val state: AdapterState,
    val capability: AdapterCapability,
    val stateMessage: String,
    val riskNotes: String,
    val requiredPermissions: String
)

/** 适配器不可用时必须抛出此异常，禁止返回伪造数据。 */
class AdapterUnavailableException(
    val platform: Platform,
    override val message: String
) : Exception(message)

/**
 * 可插拔采集适配层统一接口。
 * 主流程只依赖这个接口；每个平台一个实现，新增平台不需要改业务代码。
 */
interface PlatformAdapter {
    val platform: Platform
    val state: AdapterState
    val capability: AdapterCapability
    val stateMessage: String
    val riskNotes: String
    val requiredPermissions: String

    suspend fun searchByKeyword(query: String, limit: Int = 20): List<OfferSnapshot>

    suspend fun searchByCategory(category: String, limit: Int = 20): List<OfferSnapshot>

    suspend fun searchByShop(shopName: String, limit: Int = 20): List<OfferSnapshot>

    /** 通过商品链接 / 分享链接拉取单条报价。 */
    suspend fun fetchByLink(url: String): OfferSnapshot

    fun info(): AdapterInfo = AdapterInfo(
        platform = platform,
        state = state,
        capability = capability,
        stateMessage = stateMessage,
        riskNotes = riskNotes,
        requiredPermissions = requiredPermissions
    )
}

/** 未接入平台的通用实现：所有入口都如实抛「未可用」。 */
open class UnavailableAdapter(
    override val platform: Platform,
    override val state: AdapterState,
    override val stateMessage: String,
    override val riskNotes: String,
    override val requiredPermissions: String = "无（当前不可用）",
    override val capability: AdapterCapability = AdapterCapability()
) : PlatformAdapter {

    override suspend fun searchByKeyword(query: String, limit: Int): List<OfferSnapshot> =
        throw AdapterUnavailableException(platform, stateMessage)

    override suspend fun searchByCategory(category: String, limit: Int): List<OfferSnapshot> =
        throw AdapterUnavailableException(platform, stateMessage)

    override suspend fun searchByShop(shopName: String, limit: Int): List<OfferSnapshot> =
        throw AdapterUnavailableException(platform, stateMessage)

    override suspend fun fetchByLink(url: String): OfferSnapshot =
        throw AdapterUnavailableException(platform, stateMessage)
}
