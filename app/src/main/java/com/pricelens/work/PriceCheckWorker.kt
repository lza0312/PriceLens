package com.pricelens.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pricelens.data.AppContainer
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class PriceCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val settings = AppContainer.settingsStore.settings.first()
            val report = AppContainer.watchRepository.checkAll()
            if (settings.notifyEnabled && report.alerts.isNotEmpty()) {
                NotificationHelper.notifyAlerts(applicationContext, report.alerts)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * 定时检查调度。
 * Android 后台限制：WorkManager 最小周期 15 分钟；Doze 下会被延后；
 * 国产 ROM 需要在系统设置里允许自启动 / 后台运行，否则可能长期不执行。
 */
object WatchScheduler {

    private const val UNIQUE_PERIODIC = "pricelens_periodic_price_check"

    fun ensurePeriodic(context: Context, intervalHours: Int = 6) {
        val hours = intervalHours.coerceIn(1, 24).toLong()
        val request = PeriodicWorkRequestBuilder<PriceCheckWorker>(hours, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<PriceCheckWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
