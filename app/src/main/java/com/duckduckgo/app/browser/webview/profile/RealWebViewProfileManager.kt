/*
 * Copyright (c) 2025 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.browser.webview.profile

import android.annotation.SuppressLint
import android.webkit.CookieManager
import androidx.annotation.MainThread
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.webkit.ProfileStore
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability
import com.duckduckgo.app.browser.api.WebViewProfileManager
import com.duckduckgo.app.fire.FireproofRepository
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
@SuppressLint("RequiresFeature")
class RealWebViewProfileManager @Inject constructor(
    @WebViewProfileData private val store: DataStore<Preferences>,
    private val fireproofRepository: FireproofRepository,
    private val capabilityChecker: WebViewCapabilityChecker,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val dispatchers: DispatcherProvider,
) : WebViewProfileManager {

    @Volatile
    private var currentProfileName: String = ""

    @Volatile
    private var isInitialized: Boolean = false

    @Volatile
    private var cachedProfileSwitchingAvailable: Boolean = false

    override suspend fun isProfileSwitchingAvailable(): Boolean {
        return withContext(dispatchers.io()) {
            val available = androidBrowserConfigFeature.webViewProfiles().isEnabled() &&
                capabilityChecker.isSupported(WebViewCapability.MultiProfile)
            cachedProfileSwitchingAvailable = available
            available
        }
    }

    override fun isProfileSwitchingAvailableSync(): Boolean {
        return cachedProfileSwitchingAvailable
    }

    override fun getCurrentProfileName(): String {
        return currentProfileName
    }

    override suspend fun switchToNewProfile(): Boolean {
        if (!isProfileSwitchingAvailable()) return false

        return withContext(dispatchers.main()) {
            try {
                val oldProfileName = currentProfileName
                val newIndex = incrementProfileIndex()
                val newProfileName = getProfileName(newIndex)

                logcat { "Switching profile from '$oldProfileName' to '$newProfileName'" }

                // Create the new profile (must be on main thread)
                val webViewProfileStore = ProfileStore.getInstance()
                webViewProfileStore.getOrCreateProfile(newProfileName)

                // Migrate fireproofed cookies
                migrateFireproofedCookies(oldProfileName, newProfileName)

                // Update in-memory cache
                currentProfileName = newProfileName

                logcat { "Profile switch completed successfully to '$newProfileName'" }
                true
            } catch (e: Exception) {
                logcat(ERROR) { "Failed to switch profile: ${e.asLog()}" }
                false
            }
        }
    }

    @MainThread
    override suspend fun cleanupStaleProfiles() {
        if (!capabilityChecker.isSupported(WebViewCapability.MultiProfile)) return

        withContext(dispatchers.main()) {
            try {
                val webViewProfileStore = ProfileStore.getInstance()
                val allProfiles = webViewProfileStore.allProfileNames
                val currentProfile = currentProfileName

                logcat { "Cleaning up stale profiles. Current: '$currentProfile', All: $allProfiles" }

                allProfiles
                    .filter { it.startsWith(PROFILE_PREFIX) && it != currentProfile }
                    .forEach { staleProfile ->
                        try {
                            webViewProfileStore.deleteProfile(staleProfile)
                            logcat { "Deleted stale profile: $staleProfile" }
                        } catch (e: Exception) {
                            // Profile might be in use, will try again on next app start
                            logcat(WARN) { "Failed to delete profile $staleProfile: ${e.asLog()}" }
                        }
                    }
            } catch (e: Exception) {
                logcat(ERROR) { "Failed to cleanup stale profiles: ${e.asLog()}" }
            }
        }
    }

    override suspend fun initialize() {
        if (isInitialized) return

        withContext(dispatchers.io()) {
            try {
                // Cache availability state during initialization
                cachedProfileSwitchingAvailable = androidBrowserConfigFeature.webViewProfiles().isEnabled() &&
                    capabilityChecker.isSupported(WebViewCapability.MultiProfile)

                val currentIndex = getCurrentProfileIndex()
                currentProfileName = if (currentIndex > 0) {
                    getProfileName(currentIndex)
                } else {
                    "" // Use default profile for index 0
                }
                isInitialized = true
                logcat { "WebViewProfileManager initialized with profile: '$currentProfileName', switching available: $cachedProfileSwitchingAvailable" }
            } catch (e: Exception) {
                logcat(ERROR) { "Failed to initialize WebViewProfileManager: ${e.asLog()}" }
                currentProfileName = ""
                cachedProfileSwitchingAvailable = false
                isInitialized = true
            }
        }
    }

    // Profile store functionality (inlined from DataStoreWebViewProfileStore)

    private suspend fun getCurrentProfileIndex(): Int {
        return store.data.map { preferences ->
            preferences[KEY_PROFILE_INDEX] ?: 0
        }.firstOrNull() ?: 0
    }

    private suspend fun incrementProfileIndex(): Int {
        var newIndex = 0
        store.edit { preferences ->
            val currentIndex = preferences[KEY_PROFILE_INDEX] ?: 0
            newIndex = currentIndex + 1
            preferences[KEY_PROFILE_INDEX] = newIndex
        }
        return newIndex
    }

    private fun getProfileName(index: Int): String {
        return "$PROFILE_PREFIX$index"
    }

    // Cookie migration functionality (inlined from RealFireproofCookieMigrator)

    private suspend fun migrateFireproofedCookies(
        oldProfileName: String,
        newProfileName: String,
    ) {
        try {
            // Fetch fireproof domains on IO thread (database access)
            val fireproofDomains = withContext(dispatchers.io()) {
                fireproofRepository.fireproofWebsites()
            }
            logcat { "Migrating cookies for ${fireproofDomains.size} fireproofed domains + ${DDG_DOMAINS.size} DDG domains" }

            // ProfileStore operations must be on main thread
            withContext(dispatchers.main()) {
                val profileStore = ProfileStore.getInstance()

                // Get old profile's cookie manager (or default if empty profile name)
                val oldCookieManager = if (oldProfileName.isEmpty()) {
                    CookieManager.getInstance()
                } else {
                    profileStore.getProfile(oldProfileName)?.cookieManager ?: CookieManager.getInstance()
                }

                // Get new profile's cookie manager
                val newProfile = profileStore.getOrCreateProfile(newProfileName)
                val newCookieManager = newProfile.cookieManager

                // Migrate fireproofed domain cookies
                val domainsToMigrate = fireproofDomains.flatMap { domain ->
                    // Include both with and without https:// prefix for better cookie matching
                    listOf(domain, "https://$domain")
                } + DDG_DOMAINS

                domainsToMigrate.forEach { domain ->
                    migrateCookiesForDomain(oldCookieManager, newCookieManager, domain)
                }

                // Flush to persist cookies
                newCookieManager.flush()

                logcat { "Cookie migration completed from '$oldProfileName' to '$newProfileName'" }
            }
        } catch (e: Exception) {
            logcat(ERROR) { "Failed to migrate cookies: ${e.asLog()}" }
        }
    }

    private fun migrateCookiesForDomain(
        oldCookieManager: CookieManager,
        newCookieManager: CookieManager,
        domain: String,
    ) {
        try {
            val cookies = oldCookieManager.getCookie(domain)
            if (cookies.isNullOrEmpty()) return

            // Cookies are returned as a single string separated by "; "
            // They need to be set one by one
            cookies.split(";").forEach { cookie ->
                val trimmedCookie = cookie.trim()
                if (trimmedCookie.isNotEmpty()) {
                    newCookieManager.setCookie(domain, trimmedCookie)
                }
            }
            logcat { "Migrated cookies for domain: $domain" }
        } catch (e: Exception) {
            logcat(ERROR) { "Failed to migrate cookies for $domain: ${e.asLog()}" }
        }
    }

    private companion object {
        val KEY_PROFILE_INDEX = intPreferencesKey("PROFILE_INDEX")
        const val PROFILE_PREFIX = "ddg_default_"

        // DuckDuckGo domains that should always have cookies preserved
        val DDG_DOMAINS = listOf(
            AppUrl.Url.COOKIES,
            AppUrl.Url.SURVEY_COOKIES,
            AppUrl.Url.DUCK_AI,
        )
    }
}
