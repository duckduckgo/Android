package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.BrowserSettings
import com.example.model.OmnibarPosition
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "browser_settings")

class BrowserPreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val OMNIBAR_POSITION = stringPreferencesKey("omnibar_position")
        val TRACKER_BLOCKING = booleanPreferencesKey("tracker_blocking_enabled")
        val HTTPS_UPGRADES = booleanPreferencesKey("https_upgrades_enabled")
        val STRIP_TRACKING_PARAMS = booleanPreferencesKey("strip_tracking_params_enabled")
        val GPC_ENABLED = booleanPreferencesKey("gpc_enabled")
        val YOUTUBE_AD_BLOCKING = booleanPreferencesKey("youtube_ad_blocking_enabled")
        val AUTO_CLEAR_ON_EXIT = booleanPreferencesKey("auto_clear_on_exit")
        val TOTAL_TRACKERS_BLOCKED = longPreferencesKey("total_trackers_blocked")
        val TOTAL_HTTPS_UPGRADES = longPreferencesKey("total_https_upgrades")
        val SEEDED_DEFAULTS = booleanPreferencesKey("seeded_defaults")
        val DISABLED_PRIVACY_DOMAINS = stringPreferencesKey("disabled_privacy_domains")
    }

    val settingsFlow: Flow<BrowserSettings> = context.dataStore.data.map { prefs ->
        BrowserSettings(
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            omnibarPosition = runCatching {
                OmnibarPosition.valueOf(prefs[Keys.OMNIBAR_POSITION] ?: OmnibarPosition.TOP.name)
            }.getOrDefault(OmnibarPosition.TOP),
            trackerBlockingEnabled = prefs[Keys.TRACKER_BLOCKING] ?: true,
            httpsUpgradesEnabled = prefs[Keys.HTTPS_UPGRADES] ?: true,
            stripTrackingParamsEnabled = prefs[Keys.STRIP_TRACKING_PARAMS] ?: true,
            gpcEnabled = prefs[Keys.GPC_ENABLED] ?: true,
            youtubeAdBlockingEnabled = prefs[Keys.YOUTUBE_AD_BLOCKING] ?: true,
            autoClearOnExit = prefs[Keys.AUTO_CLEAR_ON_EXIT] ?: false,
            totalTrackersBlocked = prefs[Keys.TOTAL_TRACKERS_BLOCKED] ?: 0L,
            totalHttpsUpgrades = prefs[Keys.TOTAL_HTTPS_UPGRADES] ?: 0L,
        )
    }

    val disabledPrivacyDomainsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.DISABLED_PRIVACY_DOMAINS].orEmpty()
        if (raw.isBlank()) emptySet() else raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setOmnibarPosition(position: OmnibarPosition) {
        context.dataStore.edit { it[Keys.OMNIBAR_POSITION] = position.name }
    }

    suspend fun setTrackerBlockingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRACKER_BLOCKING] = enabled }
    }

    suspend fun setHttpsUpgradesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HTTPS_UPGRADES] = enabled }
    }

    suspend fun setStripTrackingParamsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STRIP_TRACKING_PARAMS] = enabled }
    }

    suspend fun setGpcEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GPC_ENABLED] = enabled }
    }

    suspend fun setYoutubeAdBlockingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.YOUTUBE_AD_BLOCKING] = enabled }
    }

    suspend fun setAutoClearOnExit(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CLEAR_ON_EXIT] = enabled }
    }

    suspend fun incrementTrackersBlocked(delta: Long = 1L) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.TOTAL_TRACKERS_BLOCKED] ?: 0L
            prefs[Keys.TOTAL_TRACKERS_BLOCKED] = current + delta
        }
    }

    suspend fun incrementHttpsUpgrades(delta: Long = 1L) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.TOTAL_HTTPS_UPGRADES] ?: 0L
            prefs[Keys.TOTAL_HTTPS_UPGRADES] = current + delta
        }
    }

    suspend fun toggleSitePrivacyProtection(domain: String) {
        val clean = domain.lowercase().removePrefix("www.")
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.DISABLED_PRIVACY_DOMAINS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toMutableSet() ?: mutableSetOf()
            if (current.contains(clean)) {
                current.remove(clean)
            } else {
                current.add(clean)
            }
            prefs[Keys.DISABLED_PRIVACY_DOMAINS] = current.joinToString(",")
        }
    }

    suspend fun markDefaultsSeeded(): Boolean {
        var wasSeeded = false
        context.dataStore.edit { prefs ->
            wasSeeded = prefs[Keys.SEEDED_DEFAULTS] ?: false
            if (!wasSeeded) {
                prefs[Keys.SEEDED_DEFAULTS] = true
            }
        }
        return !wasSeeded
    }
}
