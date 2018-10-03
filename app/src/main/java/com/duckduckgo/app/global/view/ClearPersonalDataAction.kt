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
import android.support.annotation.UiThread
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import com.duckduckgo.app.browser.WebDataManager
import com.duckduckgo.app.fire.FireActivity
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import timber.log.Timber
import javax.inject.Inject

class ClearPersonalDataAction @Inject constructor(
    private val context: Context,
    private val dataManager: WebDataManager,
    private val clearingStore: UnsentForgetAllPixelStore
) {

    @UiThread
    fun clear() {
        val startTime = System.currentTimeMillis()
        clearingStore.incrementCount()
        dataManager.clearData(WebView(context), WebStorage.getInstance(), context)
        dataManager.clearWebViewSessions()
        dataManager.clearExternalCookies(CookieManager.getInstance()) {
            Timber.i("Finished clearing everything; took ${System.currentTimeMillis() - startTime}ms. Restarting process")
            FireActivity.triggerRestart(context)
        }
    }
}