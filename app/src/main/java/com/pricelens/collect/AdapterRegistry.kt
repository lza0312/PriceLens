package com.pricelens.collect

import com.pricelens.collect.adapters.Ali1688Adapter
import com.pricelens.collect.adapters.DouyinAdapter
import com.pricelens.collect.adapters.JdAdapter
import com.pricelens.collect.adapters.PddAdapter
import com.pricelens.collect.adapters.TaobaoAdapter
import com.pricelens.collect.adapters.TmallAdapter
import com.pricelens.collect.adapters.WeidianAdapter
import com.pricelens.collect.net.HttpClient
import com.pricelens.domain.model.Platform

/**
 * 适配器注册表：主流程只通过这里拿适配器，新增平台时只改这一处。
 */
class AdapterRegistry(http: HttpClient = HttpClient()) {

    private val adapters: List<PlatformAdapter> = listOf(
        JdAdapter(http),
        TaobaoAdapter(),
        TmallAdapter(),
        PddAdapter(),
        DouyinAdapter(),
        WeidianAdapter(),
        Ali1688Adapter()
    )

    fun get(platform: Platform): PlatformAdapter? = adapters.firstOrNull { it.platform == platform }

    fun all(): List<PlatformAdapter> = adapters

    fun infos(): List<AdapterInfo> = adapters.map { it.info() }

    fun availableForLink(platform: Platform): Boolean =
        get(platform)?.capability?.linkFetch == true

    fun availableForKeyword(platform: Platform): Boolean =
        get(platform)?.capability?.keywordSearch == true

    fun availableForCategory(platform: Platform): Boolean =
        get(platform)?.capability?.categorySearch == true

    fun availableForShop(platform: Platform): Boolean =
        get(platform)?.capability?.shopSearch == true
}
