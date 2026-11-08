package com.pricelens.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pricelens.MainActivity
import com.pricelens.R
import com.pricelens.core.Money
import com.pricelens.data.repo.AlertEvent

object NotificationHelper {

    private const val CHANNEL_ID = "pricelens_price_drop"
    private const val CHANNEL_NAME = "降价提醒"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "仅在目标 SKU 有货且价格低于上次记录时提醒"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun notifyAlerts(context: Context, alerts: List<AlertEvent>) {
        if (alerts.isEmpty()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alerts.take(5).forEachIndexed { index, alert ->
            val text = buildString {
                append(alert.spuTitle.take(24))
                append(" · ").append(alert.skuName)
                append("\n")
                append(Money.format(alert.oldPriceCents)).append(" → ")
                append(Money.format(alert.newPriceCents))
                alert.shopName?.let { append(" · ").append(it) }
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("发现更低价（有货）")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            manager.notify(1000 + index, notification)
        }
    }
}
