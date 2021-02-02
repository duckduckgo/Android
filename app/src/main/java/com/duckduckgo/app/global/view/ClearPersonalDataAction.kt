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
import com.duckduckgo.app.browser.WebDataManager
import com.duckduckgo.app.fire.AppCacheClearer
import com.duckduckgo.app.fire.DuckDuckGoCookieManager
import com.duckduckgo.app.fire.FireActivity
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.location.GeoLocationPermissions
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

interface ClearDataAction {

    @WorkerThread
    suspend fun clearTabsAsync(appInForeground: Boolean)

    suspend fun clearTabsAndAllDataAsync(appInForeground: Boolean, shouldFireDataClearPixel: Boolean): Unit?
    fun setAppUsedSinceLastClearFlag(appUsedSinceLastClear: Boolean)
    fun killProcess()
    fun killAndRestartProcess()
}

class ClearPersonalDataAction @Inject constructor(
    private val context: Context,
    private val dataManager: WebDataManager,
    private val clearingStore: UnsentForgetAllPixelStore,
    private val tabRepository: TabRepository,
    private val settingsDataStore: SettingsDataStore,
    private val cookieManager: DuckDuckGoCookieManager,
    private val appCacheClearer: AppCacheClearer,
    private val geoLocationPermissions: GeoLocationPermissions
) : ClearDataAction {

    override fun killAndRestartProcess() {
        Timber.i("Restarting process")
        FireActivity.triggerRestart(context)
    }

    override fun killProcess() {
        Timber.i("Killing process")
        System.exit(0)
    }

    override suspend fun clearTabsAndAllDataAsync(appInForeground: Boolean, shouldFireDataClearPixel: Boolean) {
        withContext(Dispatchers.IO) {
            cookieManager.flush()
            geoLocationPermissions.clearAllButFireproofed()
            clearTabsAsync(appInForeground)
        }

        withContext(Dispatchers.Main) {
            clearDataAsync(shouldFireDataClearPixel)
        }

        Timber.i("Finished clearing everything")
    }

    @WorkerThread
    override suspend fun clearTabsAsync(appInForeground: Boolean) {
        Timber.i("Clearing tabs")
        dataManager.clearWebViewSessions()
        tabRepository.deleteAll()
        setAppUsedSinceLastClearFlag(appInForeground)
        Timber.d("Finished clearing tabs")
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

    override fun setAppUsedSinceLastClearFlag(appUsedSinceLastClear: Boolean) {
        settingsDataStore.appUsedSinceLastClear = appUsedSinceLastClear
        Timber.d("Set appUsedSinceClear flag to $appUsedSinceLastClear")
    }
}
