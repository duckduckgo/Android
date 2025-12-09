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

package com.duckduckgo.app.browser.urldisplay

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject

/**
 * Manages URL display preferences in the omnibar.
 *
 * Determines whether to show full URLs (e.g., `https://duckduckgo.com`) or shorter URLs (e.g., `duckduckgo.com`)
 * based on user preference, feature flags, and install type.
 */
interface UrlDisplayRepository {
    /**
     * Reactive flow of the URL display preference.
     * Emits when the preference changes via [setFullUrlEnabled].
     */
    val isFullUrlEnabled: Flow<Boolean>

    /**
     * Returns whether full URLs should be displayed.
     *
     * @return true for full URLs, false for shorter URLs
     */
    suspend fun isFullUrlEnabled(): Boolean

    /**
     * Sets the URL display preference and marks it as manually set by the user.
     *
     * @param enabled true for full URLs, false for shorter URLs
     */
    suspend fun setFullUrlEnabled(enabled: Boolean)
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = UrlDisplayRepository::class,
)
@SingleInstanceIn(AppScope::class)
class RealUrlDisplayRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val browserConfigFeature: AndroidBrowserConfigFeature,
    private val appBuildConfig: AppBuildConfig,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : UrlDisplayRepository {
    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 0)
    override val isFullUrlEnabled: Flow<Boolean> = flow {
        emit(isFullUrlEnabled())
        refreshTrigger.collect {
            emit(isFullUrlEnabled())
        }
    }
        .distinctUntilChanged()
        .shareIn(
            scope = appCoroutineScope,
            replay = 1,
            started = SharingStarted.WhileSubscribed(),
        )

    override suspend fun isFullUrlEnabled(): Boolean {
        // User manually set preference - honor it
        if (settingsDataStore.urlPreferenceSetByUser) {
            return settingsDataStore.isFullUrlEnabled
        }

        // Treat as manual to preserve user's choice from old app
        if (settingsDataStore.hasUrlPreferenceSet() && !settingsDataStore.urlPreferenceMigrated) {
            settingsDataStore.urlPreferenceSetByUser = true
            settingsDataStore.urlPreferenceMigrated = true
            return settingsDataStore.isFullUrlEnabled
        }

        // Feature flag disabled - rollback mode
        if (!browserConfigFeature.shorterUrlDefault().isEnabled()) {
            return true
        }

        // Preference was auto-assigned on first launch, we return cached value
        // to avoid recalculating from appBuildConfig.isNewInstall on every call
        // and keep the expected preference for future update where new user
        // won't be considered anymore as new user but has started to use the app
        // after the release of this migration feature.
        if (settingsDataStore.hasUrlPreferenceSet()) {
            return settingsDataStore.isFullUrlEnabled
        }

        // First launch: calculate and persist for future consistency
        val isNewInstall = appBuildConfig.isNewInstall()
        val defaultValue = !isNewInstall
        settingsDataStore.isFullUrlEnabled = defaultValue
        settingsDataStore.urlPreferenceMigrated = true
        return defaultValue
    }

    override suspend fun setFullUrlEnabled(enabled: Boolean) {
        settingsDataStore.urlPreferenceSetByUser = true
        settingsDataStore.urlPreferenceMigrated = true
        settingsDataStore.isFullUrlEnabled = enabled
        refreshTrigger.emit(Unit)
    }
}
