package com.pricelens

import android.app.Application
import com.pricelens.data.AppContainer
import com.pricelens.work.NotificationHelper
import com.pricelens.work.WatchScheduler

class PriceLensApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        NotificationHelper.ensureChannel(this)
        WatchScheduler.ensurePeriodic(this)
    }
}
