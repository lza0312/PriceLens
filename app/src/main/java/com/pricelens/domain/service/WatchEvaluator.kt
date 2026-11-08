package com.pricelens.domain.service

import com.pricelens.domain.model.WatchDecision

/**
 * 降价提醒判定（严格按需求）：
 * 1) 目标 SKU 必须「有货」才可能提醒；
 * 2) 价格必须严格低于「上次记录的最低有货价」；
 * 3) 缺货的更低价链接不参与判定；
 * 4) 价格没变（相等）不提醒；
 * 5) 首次记录没有「上次」，不提醒。
 */
object WatchEvaluator {

    fun evaluate(
        previousMinPriceCents: Long?,
        newMinPriceCents: Long?,
        inStock: Boolean,
        targetPriceCents: Long? = null,
        alertOnlyBelowTarget: Boolean = false
    ): WatchDecision {
        val newPrice = newMinPriceCents ?: 0L

        if (!inStock || newMinPriceCents == null) {
            return WatchDecision(
                shouldNotify = false,
                reason = "目标 SKU 当前无货，不提醒（更低价但缺货的链接不算）",
                previousPriceCents = previousMinPriceCents,
                newPriceCents = newPrice
            )
        }

        if (previousMinPriceCents == null) {
            return WatchDecision(
                shouldNotify = false,
                reason = "首次记录，没有「上次价格」可比，不提醒",
                previousPriceCents = null,
                newPriceCents = newPrice
            )
        }

        if (newMinPriceCents >= previousMinPriceCents) {
            return WatchDecision(
                shouldNotify = false,
                reason = "价格未低于上次记录（${previousMinPriceCents} → ${newMinPriceCents}，单位分），不提醒",
                previousPriceCents = previousMinPriceCents,
                newPriceCents = newPrice
            )
        }

        if (alertOnlyBelowTarget && targetPriceCents != null && newMinPriceCents > targetPriceCents) {
            return WatchDecision(
                shouldNotify = false,
                reason = "未低于设定目标价（目标 ${targetPriceCents} 分），按设置不提醒",
                previousPriceCents = previousMinPriceCents,
                newPriceCents = newPrice
            )
        }

        return WatchDecision(
            shouldNotify = true,
            reason = "SKU 有货且价格低于上次记录",
            previousPriceCents = previousMinPriceCents,
            newPriceCents = newPrice
        )
    }
}
