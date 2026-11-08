package com.pricelens.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pricelens_settings")

data class AppSettings(
    val dedupeStrict: Boolean = true,
    val imageHashEnabled: Boolean = false,
    val weightPrice: Double = 0.60,
    val weightRating: Double = 0.25,
    val weightSales: Double = 0.15,
    val alertOnlyBelowTarget: Boolean = false,
    val notifyEnabled: Boolean = true,
    val checkIntervalHours: Int = 6
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val dedupeStrict = booleanPreferencesKey("dedupe_strict")
        val imageHash = booleanPreferencesKey("image_hash")
        val weightPrice = doublePreferencesKey("weight_price")
        val weightRating = doublePreferencesKey("weight_rating")
        val weightSales = doublePreferencesKey("weight_sales")
        val alertOnlyBelowTarget = booleanPreferencesKey("alert_target_only")
        val notifyEnabled = booleanPreferencesKey("notify_enabled")
        val intervalHours = intPreferencesKey("check_interval_hours")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            dedupeStrict = prefs[Keys.dedupeStrict] ?: true,
            imageHashEnabled = prefs[Keys.imageHash] ?: false,
            weightPrice = prefs[Keys.weightPrice] ?: 0.60,
            weightRating = prefs[Keys.weightRating] ?: 0.25,
            weightSales = prefs[Keys.weightSales] ?: 0.15,
            alertOnlyBelowTarget = prefs[Keys.alertOnlyBelowTarget] ?: false,
            notifyEnabled = prefs[Keys.notifyEnabled] ?: true,
            checkIntervalHours = prefs[Keys.intervalHours] ?: 6
        )
    }

    suspend fun setDedupeStrict(value: Boolean) =
        context.dataStore.edit { it[Keys.dedupeStrict] = value }

    suspend fun setImageHashEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.imageHash] = value }

    suspend fun setWeights(price: Double, rating: Double, sales: Double) {
        val total = (price + rating + sales).takeIf { it > 0 } ?: 1.0
        context.dataStore.edit {
            it[Keys.weightPrice] = price / total
            it[Keys.weightRating] = rating / total
            it[Keys.weightSales] = sales / total
        }
    }

    suspend fun setAlertOnlyBelowTarget(value: Boolean) =
        context.dataStore.edit { it[Keys.alertOnlyBelowTarget] = value }

    suspend fun setNotifyEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.notifyEnabled] = value }

    suspend fun setCheckIntervalHours(value: Int) =
        context.dataStore.edit { it[Keys.intervalHours] = value.coerceIn(1, 24) }
}
