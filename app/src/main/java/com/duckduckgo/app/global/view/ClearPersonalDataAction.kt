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
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.annotation.UiThread
import com.duckduckgo.app.browser.WebDataManager
import com.duckduckgo.app.fire.FireActivity
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.tabs.model.TabRepository
import timber.log.Timber
import javax.inject.Inject

interface ClearDataAction {

    @UiThread
    fun clearEverything(killAndRestartProcess: Boolean = false, killProcess: Boolean = false)

    fun clearTabs()
}

class ClearPersonalDataAction @Inject constructor(
    private val context: Context,
    private val dataManager: WebDataManager,
    private val clearingStore: UnsentForgetAllPixelStore,
    private val tabRepository: TabRepository
) : ClearDataAction {

    @UiThread
    override fun clearEverything(killAndRestartProcess: Boolean, killProcess: Boolean) {
        Timber.i("Clearing everything; will restart process? $killAndRestartProcess will kill process? $killProcess")

        val startTime = System.currentTimeMillis()
        clearTabs()
        clearingStore.incrementCount()
        dataManager.clearData(WebView(context), WebStorage.getInstance(), context)
        dataManager.clearExternalCookies(CookieManager.getInstance()) {
            Timber.i("Finished clearing everything; took ${System.currentTimeMillis() - startTime}ms.")

            if (killAndRestartProcess) {
                Timber.i("Restarting process")
                FireActivity.triggerRestart(context)
            } else if(killProcess) {
                Timber.i("Killing process")
                System.exit(0)
            }
        }
    }

    override fun clearTabs() {
        Timber.i("Clearing tabs")
        dataManager.clearWebViewSessions()
        tabRepository.deleteAll()
    }
}