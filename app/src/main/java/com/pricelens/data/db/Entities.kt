package com.pricelens.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** SPU：商品级（一款手机 / 一双鞋）。 */
@Entity(
    tableName = "spus",
    indices = [Index("normalizedTitle"), Index("updatedAt")]
)
data class SpuEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val normalizedTitle: String,
    val category: String? = null,
    val brand: String? = null,
    val imageHash: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/** SKU：规格级（256GB 黑色 / 45 码）。 */
@Entity(
    tableName = "skus",
    foreignKeys = [
        ForeignKey(
            entity = SpuEntity::class,
            parentColumns = ["id"],
            childColumns = ["spuId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("spuId"), Index(value = ["spuId", "normSpec"], unique = true)]
)
data class SkuEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spuId: Long,
    val displayName: String,
    val normSpec: String,
    val specJson: String? = null,
    val createdAt: Long
)

/** 报价：链接 × 时间。同一 offerKey 的多条记录就是价格历史。 */
@Entity(
    tableName = "quotes",
    foreignKeys = [
        ForeignKey(
            entity = SkuEntity::class,
            parentColumns = ["id"],
            childColumns = ["skuId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("skuId"), Index("offerKey"), Index("capturedAt"), Index("platform")]
)
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val skuId: Long,
    val platform: String,
    val offerKey: String,
    val link: String,
    val productTitle: String,
    val priceCents: Long,
    val listPriceCents: Long? = null,
    val salesCount: Long? = null,
    val shopName: String? = null,
    val shopRating: Double? = null,
    val inStock: Boolean = true,
    val skuLabel: String? = null,
    val sourceKind: String,
    val imageUrl: String? = null,
    val note: String? = null,
    val capturedAt: Long
)

/** 关注可以绑定到具体 SKU（例如鞋盯 45 码）。 */
@Entity(
    tableName = "watches",
    foreignKeys = [
        ForeignKey(
            entity = SkuEntity::class,
            parentColumns = ["id"],
            childColumns = ["skuId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("skuId"), Index("enabled")]
)
data class WatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val skuId: Long,
    val targetPriceCents: Long? = null,
    val enabled: Boolean = true,
    val createdAt: Long,
    val lastCheckedAt: Long = 0,
    val lastNotifiedPriceCents: Long? = null
)

@Entity(
    tableName = "alerts",
    foreignKeys = [
        ForeignKey(
            entity = WatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["watchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("watchId"), Index("createdAt")]
)
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val watchId: Long,
    val skuId: Long,
    val quoteId: Long,
    val oldPriceCents: Long?,
    val newPriceCents: Long,
    val platform: String,
    val shopName: String?,
    val createdAt: Long,
    val read: Boolean = false
)
