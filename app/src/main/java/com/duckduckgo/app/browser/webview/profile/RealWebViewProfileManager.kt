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
import androidx.annotation.MainThread
import androidx.webkit.ProfileStore
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability
import com.duckduckgo.app.browser.api.WebViewProfileManager
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
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
    private val profileDataStore: WebViewProfileDataStore,
    private val migrationManager: WebViewProfileMigrationManager,
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
                androidBrowserConfigFeature.improvedDataClearingOptions().isEnabled() &&
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
                val newIndex = profileDataStore.incrementProfileIndex()
                val newProfileName = profileDataStore.getProfileName(newIndex)

                logcat { "Switching profile from '$oldProfileName' to '$newProfileName'" }

                // Create the new profile (must be on main thread)
                val webViewProfileStore = ProfileStore.getInstance()
                webViewProfileStore.getOrCreateProfile(newProfileName)

                // Migrate profile data (cookies and IndexedDB)
                migrationManager.migrateProfileData(oldProfileName, newProfileName)

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
                    .filter { it.startsWith(WebViewProfileDataStore.PROFILE_PREFIX) && it != currentProfile }
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
                cachedProfileSwitchingAvailable = isProfileSwitchingAvailable()
                if (cachedProfileSwitchingAvailable) {
                    val currentIndex = profileDataStore.getCurrentProfileIndex()
                    currentProfileName = if (currentIndex > 0) {
                        profileDataStore.getProfileName(currentIndex)
                    } else {
                        "${WebViewProfileDataStore.PROFILE_PREFIX}0"
                    }
                    logcat { "WebViewProfileManager initialized with profile: '$currentProfileName', switching available: $cachedProfileSwitchingAvailable" }
                }
            } catch (e: Exception) {
                logcat(ERROR) { "Failed to initialize WebViewProfileManager: ${e.asLog()}" }
                cachedProfileSwitchingAvailable = false
            } finally {
                isInitialized = true
            }
        }
    }

}
