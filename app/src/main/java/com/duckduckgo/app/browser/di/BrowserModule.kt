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

package com.duckduckgo.app.browser.di

import android.content.Context
import com.duckduckgo.app.browser.*
import com.duckduckgo.app.browser.defaultBrowsing.AndroidDefaultBrowserDetector
import com.duckduckgo.app.browser.defaultBrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.session.WebViewSessionInMemoryStorage
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.global.AppUrl
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class BrowserModule {

    @Provides
    fun duckDuckGoRequestRewriter(
        urlDetector: DuckDuckGoUrlDetector,
        statisticsStore: StatisticsDataStore,
        variantManager: VariantManager
    ): RequestRewriter {
        return DuckDuckGoRequestRewriter(urlDetector, statisticsStore, variantManager)
    }

    @Provides
    fun webViewLongPressHandler(): LongPressHandler {
        return WebViewLongPressHandler()
    }

    @Provides
    fun defaultWebBrowserCapability(context: Context, appInstallStore: AppInstallStore): DefaultBrowserDetector {
        return AndroidDefaultBrowserDetector(context, appInstallStore)
    }

    @Singleton
    @Provides
    fun webViewSessionStorage(): WebViewSessionStorage = WebViewSessionInMemoryStorage()

    @Singleton
    @Provides
    fun webDataManager(webViewSessionStorage: WebViewSessionStorage) = WebDataManager(AppUrl.Url.HOST, webViewSessionStorage)
}