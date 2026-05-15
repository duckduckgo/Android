/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.browsermode.impl.profile

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.browsermode.api.profile.WebViewProfileManager
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

@SuppressLint("RequiresFeature")
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = WebViewProfileManager::class)
class RealWebViewProfileManager @Inject constructor(
    private val fireModeAvailability: FireModeAvailability,
    private val dataStore: WebViewProfileDataStore,
    private val migrationManager: WebViewProfileMigrationManager,
    private val dispatchers: DispatcherProvider,
    @param:AppCoroutineScope private val appScope: CoroutineScope,
) : WebViewProfileManager {

    private val initLatch = CompletableDeferred<Unit>()
    private val mutex = Mutex()

    @Volatile
    private var activeProfiles: Map<BrowserMode, ActiveProfile> = emptyMap()

    override suspend fun initialize() {
        if (initLatch.isCompleted) return

        mutex.withLock {
            if (initLatch.isCompleted) return@withLock

            if (fireModeAvailability.isAvailable()) {
                withContext(dispatchers.main()) {
                    val store = ProfileStore.getInstance()
                    BrowserMode.entries.forEach { mode ->
                        val index = dataStore.getProfileIndex(mode)
                        val name = mode.prefix() + index
                        val profile = store.getOrCreateProfile(name)
                        activeProfiles = activeProfiles + (mode to ActiveProfile(name, profile.webStorage, profile.cookieManager))
                    }
                }
            } else {
                logcat(LogPriority.WARN) {
                    "Multi-profile unavailable; WebViewProfileManager will fall back to Default profile."
                }
            }

            initLatch.complete(Unit)
            appScope.launch { cleanupStaleProfiles() }
        }
    }

    override suspend fun getProfileName(mode: BrowserMode): String {
        initLatch.await()
        return activeProfiles[mode]?.name ?: Profile.DEFAULT_PROFILE_NAME
    }

    suspend fun getWebStorage(mode: BrowserMode): WebStorage {
        initLatch.await()
        return activeProfiles[mode]?.webStorage ?: WebStorage.getInstance()
    }

    suspend fun getCookieManager(mode: BrowserMode): CookieManager {
        initLatch.await()
        return activeProfiles[mode]?.cookieManager ?: CookieManager.getInstance()
    }

    override suspend fun clearAndRotateProfile(mode: BrowserMode): Boolean {
        initLatch.await()

        return mutex.withLock {
            val oldName = activeProfiles[mode]?.name ?: return@withLock false
            val newIndex = dataStore.incrementProfileIndex(mode)
            val newName = mode.prefix() + newIndex
            val migrationData = withContext(dispatchers.main()) {
                val store = ProfileStore.getInstance()

                val old = store.getOrCreateProfile(oldName)
                val new = store.getOrCreateProfile(newName)
                activeProfiles = activeProfiles + (mode to ActiveProfile(newName, new.webStorage, new.cookieManager))
                old to new
            }

            if (mode == BrowserMode.REGULAR) {
                migrationManager.migrate(migrationData.first, migrationData.second)
            }

            appScope.launch { cleanupStaleProfiles() }
            true
        }
    }

    override suspend fun cleanupStaleProfiles() {
        initLatch.await()

        if (fireModeAvailability.isAvailable()) {
            val active = activeProfiles.values.map { it.name }.toSet()
            withContext(dispatchers.main()) {
                val store = ProfileStore.getInstance()
                store.allProfileNames
                    .filter { name -> name.isManagedByUs() && name !in active }
                    .forEach { stale ->
                        runCatching { store.deleteProfile(stale) }.onFailure {
                            logcat(LogPriority.WARN) { "Failed to delete stale profile $stale: $it" }
                        }
                    }
            }
        }
    }

    private fun BrowserMode.prefix(): String = when (this) {
        BrowserMode.REGULAR -> "regular_"
        BrowserMode.FIRE -> "fire_"
    }

    private fun String.isManagedByUs(): Boolean =
        BrowserMode.entries.map { it.prefix() }.any { startsWith(it) }

    private data class ActiveProfile(
        val name: String,
        val webStorage: WebStorage,
        val cookieManager: CookieManager,
    )
}
