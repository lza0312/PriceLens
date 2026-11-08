package com.pricelens.data

import android.content.Context
import com.pricelens.collect.AdapterRegistry
import com.pricelens.collect.net.HttpClient
import com.pricelens.data.db.AppDatabase
import com.pricelens.data.prefs.SettingsStore
import com.pricelens.data.repo.PriceRepository
import com.pricelens.data.repo.WatchRepository
import com.pricelens.export.ExportService

object AppContainer {

    lateinit var appContext: Context
    lateinit var database: AppDatabase
    lateinit var settingsStore: SettingsStore
    lateinit var httpClient: HttpClient
    lateinit var registry: AdapterRegistry
    lateinit var priceRepository: PriceRepository
    lateinit var watchRepository: WatchRepository
    lateinit var exportService: ExportService

    fun init(context: Context) {
        if (::database.isInitialized) return
        appContext = context.applicationContext
        database = AppDatabase.build(appContext)
        settingsStore = SettingsStore(appContext)
        httpClient = HttpClient()
        registry = AdapterRegistry(httpClient)
        priceRepository = PriceRepository(database, settingsStore)
        watchRepository = WatchRepository(database, settingsStore, registry, priceRepository)
        exportService = ExportService(database)
    }
}
