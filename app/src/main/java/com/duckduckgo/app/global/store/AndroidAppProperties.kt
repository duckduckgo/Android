/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.global.store

import android.content.Context
import androidx.webkit.WebViewCompat
import com.duckduckgo.app.playstore.PlayStoreUtils
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.browser.api.AppProperties

class AndroidAppProperties(
    private val appContext: Context,
    private val variantManager: VariantManager,
    private val playStoreUtils: PlayStoreUtils,
    private val statisticsStore: StatisticsDataStore
) : AppProperties {

    override fun atb(): String {
        return statisticsStore.atb?.version.orEmpty()
    }

    override fun appAtb(): String {
        return statisticsStore.appRetentionAtb.orEmpty()
    }

    override fun searchAtb(): String {
        return statisticsStore.searchRetentionAtb.orEmpty()
    }

    override fun expVariant(): String {
        return variantManager.getVariant().key
    }

    override fun installedGPlay(): Boolean {
        return playStoreUtils.installedFromPlayStore()
    }

    override fun webView(): String {
        return WebViewCompat.getCurrentWebViewPackage(appContext)?.versionName ?: WEBVIEW_UNKNOWN_VERSION
    }

    companion object {
        const val WEBVIEW_UNKNOWN_VERSION = "unknown"
    }
}
