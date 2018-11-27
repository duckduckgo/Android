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
import com.duckduckgo.app.fire.FireActivity
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
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

    suspend fun clearTabsAndAllDataAsync(appInForeground: Boolean): Unit?

    fun killProcess()
    fun killAndRestartProcess()
}

class ClearPersonalDataAction @Inject constructor(
    private val context: Context,
    private val dataManager: WebDataManager,
    private val clearingStore: UnsentForgetAllPixelStore,
    private val tabRepository: TabRepository,
    private val settingsDataStore: SettingsDataStore
) : ClearDataAction, CoroutineScope {

    private val clearJob: Job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + clearJob

    override fun killAndRestartProcess() {
        Timber.i("Restarting process")
        FireActivity.triggerRestart(context)
    }

    override fun killProcess() {
        Timber.i("Killing process")
        System.exit(0)
    }

    override suspend fun clearTabsAndAllDataAsync(appInForeground: Boolean) {
        val startTime = System.currentTimeMillis()

        withContext(Dispatchers.IO) {
            clearTabsAsync(appInForeground)
        }

        withContext(Dispatchers.Main) {
            clearDataAsync()
        }

        Timber.i("Finished clearing everything; took ${System.currentTimeMillis() - startTime}ms.")
    }

    @WorkerThread
    override suspend fun clearTabsAsync(appInForeground: Boolean) {
        val startTime = System.currentTimeMillis()
        Timber.i("Clearing tabs")

        dataManager.clearWebViewSessions()
        tabRepository.deleteAll()

        Timber.d("Setting appUsedSinceClear flag to $appInForeground")
        settingsDataStore.appUsedSinceLastClear = appInForeground

        Timber.i("Finished clearing tabs; took ${System.currentTimeMillis() - startTime}ms.")
    }

    @UiThread
    private suspend fun clearDataAsync() {
        val startTime = System.currentTimeMillis()
        clearingStore.incrementCount()
        dataManager.clearData(WebView(context), WebStorage.getInstance(), context)
        dataManager.clearExternalCookies()

        Timber.i("Finished clearing data; took ${System.currentTimeMillis() - startTime}ms.")
    }
}