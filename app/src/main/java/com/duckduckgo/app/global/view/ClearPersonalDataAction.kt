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
import android.util.Log
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import com.duckduckgo.app.browser.WebDataManager
import com.duckduckgo.app.fire.DuckDuckGoCookieManager
import com.duckduckgo.app.fire.FireActivity
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.global.performance.measureExecution
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

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
    private val cookieManager: DuckDuckGoCookieManager
) : ClearDataAction, CoroutineScope {

    private val clearJob: Job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + clearJob

    override fun killAndRestartProcess() {
        Timber.i("Restarting process")
        FireActivity.triggerRestart(context)

        clearJob.cancel()
    }

    override fun killProcess() {
        Timber.i("Killing process")
        System.exit(0)
    }

    override suspend fun clearTabsAndAllDataAsync(appInForeground: Boolean, shouldFireDataClearPixel: Boolean) {
        measureExecution("Finished clearing everything", Log.INFO) {
            withContext(Dispatchers.IO) {
                cookieManager.flush()
                clearTabsAsync(appInForeground)
            }

            withContext(Dispatchers.Main) {
                clearDataAsync(shouldFireDataClearPixel)
            }
        }
    }

    @WorkerThread
    override suspend fun clearTabsAsync(appInForeground: Boolean) {
        measureExecution("Finished clearing tabs", Log.INFO) {
            Timber.i("Clearing tabs")
            dataManager.clearWebViewSessions()
            tabRepository.deleteAll()
            setAppUsedSinceLastClearFlag(appInForeground)
        }
    }

    @UiThread
    private suspend fun clearDataAsync(shouldFireDataClearPixel: Boolean) {
        measureExecution("Finished clearing data", Log.INFO) {
            Timber.i("Clearing data")

            if (shouldFireDataClearPixel) {
                measureExecution("Incremented data-clear pixel count") { clearingStore.incrementCount() }
            }

            dataManager.clearData(createWebView(), createWebStorage(), context)
            dataManager.clearExternalCookies()
        }
    }

    private fun createWebView(): WebView {
        return measureExecution("Created WebView instance for clearing") {
            WebView(context)
        }
    }

    private fun createWebStorage(): WebStorage {
        return measureExecution("Created web storage") {
            WebStorage.getInstance()
        }
    }

    override fun setAppUsedSinceLastClearFlag(appUsedSinceLastClear: Boolean) {
        measureExecution("Set appUsedSinceClear flag to $appUsedSinceLastClear") {
            settingsDataStore.appUsedSinceLastClear = appUsedSinceLastClear
        }
    }
}