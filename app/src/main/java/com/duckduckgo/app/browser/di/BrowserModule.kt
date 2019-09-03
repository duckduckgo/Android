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

import android.content.ClipboardManager
import android.content.Context
import android.webkit.CookieManager
import com.duckduckgo.app.browser.*
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.browser.addtohome.AddToHomeSystemCapabilityDetector
import com.duckduckgo.app.browser.defaultbrowsing.AndroidDefaultBrowserDetector
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserObserver
import com.duckduckgo.app.browser.session.WebViewSessionInMemoryStorage
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.browser.tabpreview.FileBasedWebViewPreviewGenerator
import com.duckduckgo.app.browser.tabpreview.FileBasedWebViewPreviewPersister
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewGenerator
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.fire.DuckDuckGoCookieManager
import com.duckduckgo.app.fire.WebViewCookieManager
import com.duckduckgo.app.global.AppUrl
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.OfflinePixelDataStore
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.surrogates.ResourceSurrogates
import com.duckduckgo.app.tabs.ui.GridViewColumnCalculator
import com.duckduckgo.app.trackerdetection.TrackerDetector
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
    fun browserWebViewClient(
        requestRewriter: RequestRewriter,
        specialUrlDetector: SpecialUrlDetector,
        requestInterceptor: RequestInterceptor,
        offlinePixelDataStore: OfflinePixelDataStore
    ): BrowserWebViewClient {
        return BrowserWebViewClient(requestRewriter, specialUrlDetector, requestInterceptor, offlinePixelDataStore)
    }

    @Provides
    fun webViewLongPressHandler(context: Context, pixel: Pixel): LongPressHandler {
        return WebViewLongPressHandler(context, pixel)
    }

    @Provides
    fun defaultWebBrowserCapability(context: Context): DefaultBrowserDetector {
        return AndroidDefaultBrowserDetector(context)
    }

    @Provides
    fun defaultBrowserObserver(
        defaultBrowserDetector: DefaultBrowserDetector,
        appInstallStore: AppInstallStore,
        pixel: Pixel
    ): DefaultBrowserObserver {
        return DefaultBrowserObserver(defaultBrowserDetector, appInstallStore, pixel)
    }

    @Singleton
    @Provides
    fun webViewSessionStorage(): WebViewSessionStorage = WebViewSessionInMemoryStorage()

    @Singleton
    @Provides
    fun webDataManager(
        context: Context,
        webViewSessionStorage: WebViewSessionStorage,
        cookieManager: DuckDuckGoCookieManager,
        fileDeleter: FileDeleter
    ): WebDataManager =
        WebViewDataManager(context, webViewSessionStorage, cookieManager, fileDeleter)

    @Provides
    fun clipboardManager(context: Context): ClipboardManager {
        return context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    @Provides
    fun addToHomeCapabilityDetector(context: Context): AddToHomeCapabilityDetector {
        return AddToHomeSystemCapabilityDetector(context)
    }

    @Provides
    fun specialUrlDetector(): SpecialUrlDetector = SpecialUrlDetectorImpl()

    @Provides
    fun webViewRequestInterceptor(
        resourceSurrogates: ResourceSurrogates,
        trackerDetector: TrackerDetector,
        httpsUpgrader: HttpsUpgrader,
        privacyProtectionCountDao: PrivacyProtectionCountDao
    ): RequestInterceptor = WebViewRequestInterceptor(resourceSurrogates, trackerDetector, httpsUpgrader, privacyProtectionCountDao)

    @Provides
    fun cookieManager(cookieManager: CookieManager): DuckDuckGoCookieManager {
        return WebViewCookieManager(cookieManager, AppUrl.Url.HOST)
    }

    @Singleton
    @Provides
    fun webViewCookieManager(): CookieManager {
        return CookieManager.getInstance()
    }

    @Singleton
    @Provides
    fun gridViewColumnCalculator(context: Context): GridViewColumnCalculator {
        return GridViewColumnCalculator(context)
    }

    @Singleton
    @Provides
    fun webViewPreviewPersister(context: Context, fileDeleter: FileDeleter): WebViewPreviewPersister {
        return FileBasedWebViewPreviewPersister(context, fileDeleter)
    }

    @Provides
    fun webViewPreviewGenerator(): WebViewPreviewGenerator {
        return FileBasedWebViewPreviewGenerator()
    }
}