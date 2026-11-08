package com.aura.music.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.auraStore by preferencesDataStore("aura_settings")

/** theme: 0 system, 1 light, 2 dark */
data class AppSettings(val theme: Int = 0, val accent: Int = 0, val gradient: Int = 0, val keepAwake: Boolean = false)

class SettingsStore(private val ctx: Context) {
    private val kTheme = intPreferencesKey("theme")
    private val kAccent = intPreferencesKey("accent")
    private val kGradient = intPreferencesKey("gradient")
    private val kAwake = booleanPreferencesKey("keep_awake")

    val flow: Flow<AppSettings> = ctx.auraStore.data.map {
        AppSettings(it[kTheme] ?: 0, it[kAccent] ?: 0, it[kGradient] ?: 0, it[kAwake] ?: false)
    }

    suspend fun setTheme(v: Int) { ctx.auraStore.edit { it[kTheme] = v } }
    suspend fun setAccent(v: Int) { ctx.auraStore.edit { it[kAccent] = v } }
    suspend fun setGradient(v: Int) { ctx.auraStore.edit { it[kGradient] = v } }
    suspend fun setKeepAwake(v: Boolean) { ctx.auraStore.edit { it[kAwake] = v } }
}
