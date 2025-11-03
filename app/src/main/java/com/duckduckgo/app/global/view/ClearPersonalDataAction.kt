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
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import com.duckduckgo.adclick.api.AdClickManager
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
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupDataClearer
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import com.duckduckgo.sync.api.DeviceSyncState
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.logcat

interface ClearDataAction {

    @WorkerThread
    suspend fun clearTabsAsync(appInForeground: Boolean)

    suspend fun clearTabsAndAllDataAsync(
        appInForeground: Boolean,
        shouldFireDataClearPixel: Boolean,
    ): Unit?

    suspend fun setAppUsedSinceLastClearFlag(appUsedSinceLastClear: Boolean)
    fun killProcess()
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
    private val adClickManager: AdClickManager,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val sitePermissionsManager: SitePermissionsManager,
    private val deviceSyncState: DeviceSyncState,
    private val savedSitesRepository: SavedSitesRepository,
    private val privacyProtectionsPopupDataClearer: PrivacyProtectionsPopupDataClearer,
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

            privacyProtectionsPopupDataClearer.clearPersonalData()

            clearTabsAsync(appInForeground)

            webTrackersBlockedRepository.deleteAll()

            navigationHistory.clearHistory()
        }

        withContext(dispatchers.main()) {
            clearDataAsync(shouldFireDataClearPixel)
        }

        logcat(INFO) { "Finished clearing everything" }
    }

    @WorkerThread
    override suspend fun clearTabsAsync(appInForeground: Boolean) {
        withContext(dispatchers.io()) {
            logcat(INFO) { "Clearing tabs" }
            dataManager.clearWebViewSessions()
            // tabRepository.deleteAll()
            tabRepository.deleteAll()
            adClickManager.clearAll()
            setAppUsedSinceLastClearFlag(appInForeground)
            logcat { "Finished clearing tabs" }
        }
    }

    @UiThread
    private suspend fun clearDataAsync(shouldFireDataClearPixel: Boolean) {
        logcat(INFO) { "Clearing data" }

        if (shouldFireDataClearPixel) {
            clearingStore.incrementCount()
        }

        dataManager.clearData(createWebView(), createWebStorage())
        appCacheClearer.clearCache()

        logcat(INFO) { "Finished clearing data" }
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
