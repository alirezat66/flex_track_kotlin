package dev.flextrack.sample.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private val Context.demoDataStore by preferencesDataStore("flextrack_sample")

@Singleton
class DemoPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val networkKey = booleanPreferencesKey("network_available")
    private val consentKey = booleanPreferencesKey("analytics_consent")
    private val onlineCache = AtomicBoolean(true)
    private val consentCache = AtomicBoolean(true)

    val isOnline: Flow<Boolean> = context.demoDataStore.data
        .map { it[networkKey] ?: true }
    val hasConsent: Flow<Boolean> = context.demoDataStore.data
        .map { it[consentKey] ?: true }

    fun onlineNow(): Boolean = onlineCache.get()
    fun consentNow(): Boolean = consentCache.get()

    suspend fun initialize() {
        onlineCache.set(isOnline.first())
        consentCache.set(hasConsent.first())
    }

    suspend fun setOnline(value: Boolean) {
        onlineCache.set(value)
        context.demoDataStore.edit { it[networkKey] = value }
    }

    suspend fun setConsent(value: Boolean) {
        consentCache.set(value)
        context.demoDataStore.edit { it[consentKey] = value }
    }
}
