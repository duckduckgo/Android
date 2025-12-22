/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.global.view

import android.content.Context
import android.webkit.WebStorage
import android.webkit.WebView
import com.duckduckgo.app.browser.WebDataManager
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.fire.AppCacheClearer
import com.duckduckgo.app.fire.FireActivity
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.api.WebTrackersBlockedRepository
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.api.DuckDuckGoCookieManager
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import com.duckduckgo.sync.api.DeviceSyncState
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.logcat

interface ClearDataAction {
    /**
     * Clears tabs and all browser data (legacy full clear).
     * @param appInForeground whether the app is in foreground
     * @param shouldFireDataClearPixel whether to fire the data clear pixel
     */
    suspend fun clearTabsAndAllDataAsync(
        appInForeground: Boolean,
        shouldFireDataClearPixel: Boolean,
    ): Unit?

    /**
     * Clears tabs and associated data.
     * @param appInForeground whether the app is in foreground
     */
    suspend fun clearTabsAsync(appInForeground: Boolean)

    /**
     * Clears tabs and associated data.
     */
    suspend fun clearTabsOnly()

    /**
     * Clears browser data except tabs and chats.
     * @param shouldFireDataClearPixel whether to fire the data clear pixel
     */
    suspend fun clearBrowserDataOnly(shouldFireDataClearPixel: Boolean)

    /**
     * Clears only DuckAi chats.
     */
    suspend fun clearDuckAiChatsOnly()

    /**
     * Sets the flag indicating whether the app has been used since the last data clear.
     * @param appUsedSinceLastClear true if the app has been used since the last clear, false otherwise
     */
    suspend fun setAppUsedSinceLastClearFlag(appUsedSinceLastClear: Boolean)

    /**
     * Kills the current process.
     */
    fun killProcess()

    /**
     * Kills and restarts the current process.
     * @param notifyDataCleared whether to notify that data has been cleared
     * @param enableTransitionAnimation whether to enable transition animation during restart
     */
    fun killAndRestartProcess(notifyDataCleared: Boolean, enableTransitionAnimation: Boolean = true)
}

class ClearPersonalDataAction(
    private val context: Context,
    private val dataManager: WebDataManager,
    private val clearingStore: UnsentForgetAllPixelStore,
    private val tabRepository: TabRepository,
    private val settingsDataStore: SettingsDataStore,
    private val cookieManager: DuckDuckGoCookieManager,
    private val appCacheClearer: AppCacheClearer,
    private val thirdPartyCookieManager: ThirdPartyCookieManager,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val sitePermissionsManager: SitePermissionsManager,
    private val deviceSyncState: DeviceSyncState,
    private val savedSitesRepository: SavedSitesRepository,
    private val navigationHistory: NavigationHistory,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val webTrackersBlockedRepository: WebTrackersBlockedRepository,
) : ClearDataAction {

    override fun killAndRestartProcess(notifyDataCleared: Boolean, enableTransitionAnimation: Boolean) {
        logcat(INFO) { "Restarting process" }
        FireActivity.triggerRestart(context, notifyDataCleared, enableTransitionAnimation)
    }

    override fun killProcess() {
        logcat(INFO) { "Killing process" }
        System.exit(0)
    }

    override suspend fun clearTabsAndAllDataAsync(
        appInForeground: Boolean,
        shouldFireDataClearPixel: Boolean,
    ) {
        withContext(dispatchers.io()) {
            val fireproofDomains = fireproofWebsiteRepository.fireproofWebsitesSync().map { it.domain }
            cookieManager.flush()
            sitePermissionsManager.clearAllButFireproof(fireproofDomains)
            thirdPartyCookieManager.clearAllData()

            // https://app.asana.com/0/69071770703008/1204375817149200/f
            if (!deviceSyncState.isUserSignedInOnDevice()) {
                savedSitesRepository.pruneDeleted()
            }

            clearTabsAsync(appInForeground)

            webTrackersBlockedRepository.deleteAll()

            navigationHistory.clearHistory()
        }

        clearDataAsync(shouldFireDataClearPixel)

        logcat(INFO) { "Finished clearing everything" }
    }

    override suspend fun clearTabsAsync(appInForeground: Boolean) {
        withContext(dispatchers.io()) {
            tabRepository.deleteAll()
            setAppUsedSinceLastClearFlag(appInForeground)
            logcat { "Finished clearing tabs" }
        }
    }

    override suspend fun clearTabsOnly() {
        withContext(dispatchers.io()) {
            tabRepository.deleteAll()
            logcat { "Finished clearing tabs" }
        }
    }

    override suspend fun clearBrowserDataOnly(shouldFireDataClearPixel: Boolean) {
        withContext(dispatchers.io()) {
            val fireproofDomains = fireproofWebsiteRepository.fireproofWebsitesSync().map { it.domain }
            cookieManager.flush()
            sitePermissionsManager.clearAllButFireproof(fireproofDomains)
            thirdPartyCookieManager.clearAllData()

            // https://app.asana.com/0/69071770703008/1204375817149200/f
            if (!deviceSyncState.isUserSignedInOnDevice()) {
                savedSitesRepository.pruneDeleted()
            }

            webTrackersBlockedRepository.deleteAll()

            navigationHistory.clearHistory()
        }

        clearDataGranularlyAsync(shouldFireDataClearPixel)

        logcat(INFO) { "Finished clearing browser data" }
    }

    override suspend fun clearDuckAiChatsOnly() {
        withContext(dispatchers.main()) {
            dataManager.clearData(
                webView = createWebView(),
                webStorage = createWebStorage(),
                shouldClearBrowserData = false,
                shouldClearDuckAiData = true,
            )

            logcat(INFO) { "Finished clearing chats" }
        }
    }

    private suspend fun clearDataAsync(shouldFireDataClearPixel: Boolean) {
        withContext(dispatchers.main()) {
            if (shouldFireDataClearPixel) {
                clearingStore.incrementCount()
            }

            dataManager.clearData(createWebView(), createWebStorage())
            appCacheClearer.clearCache()

            logcat(INFO) { "Finished clearing data" }
        }
    }

    private suspend fun clearDataGranularlyAsync(shouldFireDataClearPixel: Boolean) {
        withContext(dispatchers.main()) {
            if (shouldFireDataClearPixel) {
                clearingStore.incrementCount()
            }

            dataManager.clearData(
                webView = createWebView(),
                webStorage = createWebStorage(),
                shouldClearBrowserData = true,
                shouldClearDuckAiData = false,
            )
            appCacheClearer.clearCache()

            logcat(INFO) { "Finished clearing data" }
        }
    }

    private fun createWebView(): WebView {
        return WebView(context)
    }

    private fun createWebStorage(): WebStorage {
        return WebStorage.getInstance()
    }

    override suspend fun setAppUsedSinceLastClearFlag(appUsedSinceLastClear: Boolean) {
        withContext(dispatchers.io()) {
            settingsDataStore.appUsedSinceLastClear = appUsedSinceLastClear
            logcat { "Set appUsedSinceClear flag to $appUsedSinceLastClear" }
        }
    }
}
