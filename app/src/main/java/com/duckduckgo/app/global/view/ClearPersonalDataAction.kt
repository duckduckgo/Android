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
import com.duckduckgo.app.location.GeoLocationPermissions
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.api.DuckDuckGoCookieManager
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import com.duckduckgo.sync.api.DeviceSyncState
import kotlinx.coroutines.withContext
import timber.log.Timber

interface ClearDataAction {

    @WorkerThread
    suspend fun clearTabsAsync(appInForeground: Boolean)

    suspend fun clearTabsAndAllDataAsync(
        appInForeground: Boolean,
        shouldFireDataClearPixel: Boolean,
    ): Unit?

    suspend fun setAppUsedSinceLastClearFlag(appUsedSinceLastClear: Boolean)
    fun killProcess()
    fun killAndRestartProcess(notifyDataCleared: Boolean)
}

class ClearPersonalDataAction(
    private val context: Context,
    private val dataManager: WebDataManager,
    private val clearingStore: UnsentForgetAllPixelStore,
    private val tabRepository: TabRepository,
    private val settingsDataStore: SettingsDataStore,
    private val cookieManager: DuckDuckGoCookieManager,
    private val appCacheClearer: AppCacheClearer,
    private val geoLocationPermissions: GeoLocationPermissions,
    private val thirdPartyCookieManager: ThirdPartyCookieManager,
    private val adClickManager: AdClickManager,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val sitePermissionsManager: SitePermissionsManager,
    private val deviceSyncState: DeviceSyncState,
    private val savedSitesRepository: SavedSitesRepository,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : ClearDataAction {

    override fun killAndRestartProcess(notifyDataCleared: Boolean) {
        Timber.i("Restarting process")
        FireActivity.triggerRestart(context, notifyDataCleared)
    }

    override fun killProcess() {
        Timber.i("Killing process")
        System.exit(0)
    }

    override suspend fun clearTabsAndAllDataAsync(
        appInForeground: Boolean,
        shouldFireDataClearPixel: Boolean,
    ) {
        withContext(dispatchers.io()) {
            val fireproofDomains = fireproofWebsiteRepository.fireproofWebsitesSync().map { it.domain }
            cookieManager.flush()
            geoLocationPermissions.clearAllButFireproofed()
            sitePermissionsManager.clearAllButFireproof(fireproofDomains)
            thirdPartyCookieManager.clearAllData()

            // https://app.asana.com/0/69071770703008/1204375817149200/f
            if (!deviceSyncState.isUserSignedInOnDevice()) {
                savedSitesRepository.pruneDeleted()
            }

            clearTabsAsync(appInForeground)
        }

        withContext(dispatchers.main()) {
            clearDataAsync(shouldFireDataClearPixel)
        }

        Timber.i("Finished clearing everything")
    }

    @WorkerThread
    override suspend fun clearTabsAsync(appInForeground: Boolean) {
        withContext(dispatchers.io()) {
            Timber.i("Clearing tabs")
            dataManager.clearWebViewSessions()
            tabRepository.deleteAll()
            adClickManager.clearAll()
            setAppUsedSinceLastClearFlag(appInForeground)
            Timber.d("Finished clearing tabs")
        }
    }

    @UiThread
    private suspend fun clearDataAsync(shouldFireDataClearPixel: Boolean) {
        Timber.i("Clearing data")

        if (shouldFireDataClearPixel) {
            clearingStore.incrementCount()
        }

        dataManager.clearData(createWebView(), createWebStorage())
        appCacheClearer.clearCache()

        Timber.i("Finished clearing data")
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
            Timber.d("Set appUsedSinceClear flag to $appUsedSinceLastClear")
        }
    }
}
