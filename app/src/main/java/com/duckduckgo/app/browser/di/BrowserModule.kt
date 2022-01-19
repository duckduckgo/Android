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
import android.content.pm.PackageManager
import android.webkit.CookieManager
import androidx.lifecycle.LifecycleObserver
import com.duckduckgo.app.accessibility.AccessibilityManager
import com.duckduckgo.app.browser.*
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.browser.addtohome.AddToHomeSystemCapabilityDetector
import com.duckduckgo.app.browser.certificates.rootstore.TrustedCertificateStore
import com.duckduckgo.app.browser.cookies.AppThirdPartyCookieManager
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.browser.cookies.db.AuthCookiesAllowedDomainsRepository
import com.duckduckgo.app.browser.defaultbrowsing.AndroidDefaultBrowserDetector
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserObserver
import com.duckduckgo.app.browser.downloader.*
import com.duckduckgo.app.browser.favicon.FaviconPersister
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.logindetection.*
import com.duckduckgo.app.browser.serviceworker.ServiceWorkerLifecycleObserver
import com.duckduckgo.app.browser.session.WebViewSessionInMemoryStorage
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.browser.tabpreview.FileBasedWebViewPreviewGenerator
import com.duckduckgo.app.browser.tabpreview.FileBasedWebViewPreviewPersister
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewGenerator
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.browser.urlextraction.DOMUrlExtractor
import com.duckduckgo.app.browser.urlextraction.JsUrlExtractor
import com.duckduckgo.app.browser.urlextraction.UrlExtractingWebViewClient
import com.duckduckgo.app.browser.useragent.UserAgentProvider
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.email.EmailInjector
import com.duckduckgo.app.fire.*
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.AppUrl
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.device.DeviceInfo
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.referral.AppReferrerDataStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.ExceptionPixel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.surrogates.ResourceSurrogates
import com.duckduckgo.app.tabs.ui.GridViewColumnCalculator
import com.duckduckgo.app.trackerdetection.TrackerDetector
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.TrackingLinkDetector
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Provider

@Module
class BrowserModule {

    @Provides
    fun duckDuckGoRequestRewriter(
        urlDetector: DuckDuckGoUrlDetector,
        statisticsStore: StatisticsDataStore,
        variantManager: VariantManager,
        appReferrerDataStore: AppReferrerDataStore
    ): RequestRewriter {
        return DuckDuckGoRequestRewriter(urlDetector, statisticsStore, variantManager, appReferrerDataStore)
    }

    @Provides
    fun browserWebViewClient(
        webViewHttpAuthStore: WebViewHttpAuthStore,
        trustedCertificateStore: TrustedCertificateStore,
        requestRewriter: RequestRewriter,
        specialUrlDetector: SpecialUrlDetector,
        requestInterceptor: RequestInterceptor,
        offlinePixelCountDataStore: OfflinePixelCountDataStore,
        uncaughtExceptionRepository: UncaughtExceptionRepository,
        cookieManager: CookieManager,
        loginDetector: DOMLoginDetector,
        dosDetector: DosDetector,
        gpc: Gpc,
        thirdPartyCookieManager: ThirdPartyCookieManager,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        emailInjector: EmailInjector,
        accessibilityManager: AccessibilityManager
    ): BrowserWebViewClient {
        return BrowserWebViewClient(
            webViewHttpAuthStore,
            trustedCertificateStore,
            requestRewriter,
            specialUrlDetector,
            requestInterceptor,
            offlinePixelCountDataStore,
            uncaughtExceptionRepository,
            cookieManager,
            loginDetector,
            dosDetector,
            gpc,
            thirdPartyCookieManager,
            appCoroutineScope,
            dispatcherProvider,
            emailInjector,
            accessibilityManager
        )
    }

    @Provides
    fun urlExtractingWebViewClient(
        webViewHttpAuthStore: WebViewHttpAuthStore,
        trustedCertificateStore: TrustedCertificateStore,
        requestInterceptor: RequestInterceptor,
        cookieManager: CookieManager,
        gpc: Gpc,
        thirdPartyCookieManager: ThirdPartyCookieManager,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        urlExtractor: DOMUrlExtractor
    ): UrlExtractingWebViewClient {
        return UrlExtractingWebViewClient(
            webViewHttpAuthStore,
            trustedCertificateStore,
            requestInterceptor,
            cookieManager,
            gpc,
            thirdPartyCookieManager,
            appCoroutineScope,
            dispatcherProvider,
            urlExtractor
        )
    }

    @Provides
    fun webViewLongPressHandler(
        context: Context,
        pixel: Pixel
    ): LongPressHandler {
        return WebViewLongPressHandler(context, pixel)
    }

    @Provides
    fun defaultWebBrowserCapability(
        context: Context,
        appBuildConfig: AppBuildConfig
    ): DefaultBrowserDetector {
        return AndroidDefaultBrowserDetector(context, appBuildConfig)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    @IntoSet
    fun defaultBrowserObserver(
        defaultBrowserDetector: DefaultBrowserDetector,
        appInstallStore: AppInstallStore,
        pixel: Pixel
    ): LifecycleObserver {
        return DefaultBrowserObserver(defaultBrowserDetector, appInstallStore, pixel)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun webViewSessionStorage(): WebViewSessionStorage = WebViewSessionInMemoryStorage()

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun webDataManager(
        context: Context,
        webViewSessionStorage: WebViewSessionStorage,
        cookieManager: DuckDuckGoCookieManager,
        fileDeleter: FileDeleter,
        webViewHttpAuthStore: WebViewHttpAuthStore
    ): WebDataManager =
        WebViewDataManager(context, webViewSessionStorage, cookieManager, fileDeleter, webViewHttpAuthStore)

    @Provides
    fun clipboardManager(context: Context): ClipboardManager {
        return context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    @Provides
    fun addToHomeCapabilityDetector(context: Context): AddToHomeCapabilityDetector {
        return AddToHomeSystemCapabilityDetector(context)
    }

    @Provides
    fun specialUrlDetector(packageManager: PackageManager, trackingLinkDetector: TrackingLinkDetector): SpecialUrlDetector = SpecialUrlDetectorImpl(packageManager, trackingLinkDetector)

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun userAgentProvider(
        @Named("defaultUserAgent") defaultUserAgent: Provider<String>,
        deviceInfo: DeviceInfo
    ): UserAgentProvider {
        return UserAgentProvider(defaultUserAgent, deviceInfo)
    }

    @Provides
    fun webViewRequestInterceptor(
        resourceSurrogates: ResourceSurrogates,
        trackerDetector: TrackerDetector,
        httpsUpgrader: HttpsUpgrader,
        privacyProtectionCountDao: PrivacyProtectionCountDao,
        gpc: Gpc,
        userAgentProvider: UserAgentProvider
    ): RequestInterceptor =
        WebViewRequestInterceptor(resourceSurrogates, trackerDetector, httpsUpgrader, privacyProtectionCountDao, gpc, userAgentProvider)

    @Provides
    fun cookieManager(
        cookieManager: CookieManager,
        removeCookies: RemoveCookies,
        dispatcherProvider: DispatcherProvider
    ): DuckDuckGoCookieManager {
        return WebViewCookieManager(cookieManager, AppUrl.Url.COOKIES, removeCookies, dispatcherProvider)
    }

    @Provides
    fun removeCookiesStrategy(
        cookieManagerRemover: CookieManagerRemover,
        sqlCookieRemover: SQLCookieRemover
    ): RemoveCookies {
        return RemoveCookies(cookieManagerRemover, sqlCookieRemover)
    }

    @Provides
    fun sqlCookieRemover(
        @Named("webViewDbLocator") webViewDatabaseLocator: DatabaseLocator,
        getCookieHostsToPreserve: GetCookieHostsToPreserve,
        offlinePixelCountDataStore: OfflinePixelCountDataStore,
        exceptionPixel: ExceptionPixel,
        dispatcherProvider: DispatcherProvider
    ): SQLCookieRemover {
        return SQLCookieRemover(webViewDatabaseLocator, getCookieHostsToPreserve, offlinePixelCountDataStore, exceptionPixel, dispatcherProvider)
    }

    @Provides
    @Named("webViewDbLocator")
    fun webViewDatabaseLocator(context: Context): DatabaseLocator = WebViewDatabaseLocator(context)

    @Provides
    @Named("authDbLocator")
    fun authDatabaseLocator(context: Context): DatabaseLocator = AuthDatabaseLocator(context)

    @Provides
    fun databaseCleanerHelper(): DatabaseCleaner = DatabaseCleanerHelper()

    @Provides
    fun getCookieHostsToPreserve(fireproofWebsiteDao: FireproofWebsiteDao): GetCookieHostsToPreserve = GetCookieHostsToPreserve(fireproofWebsiteDao)

    @Provides
    fun cookieManagerRemover(
        cookieManager: CookieManager
    ): CookieManagerRemover {
        return CookieManagerRemover(cookieManager)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun webViewCookieManager(): CookieManager {
        return CookieManager.getInstance()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun gridViewColumnCalculator(context: Context): GridViewColumnCalculator {
        return GridViewColumnCalculator(context)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun webViewPreviewPersister(
        context: Context,
        fileDeleter: FileDeleter
    ): WebViewPreviewPersister {
        return FileBasedWebViewPreviewPersister(context, fileDeleter)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun faviconPersister(
        context: Context,
        fileDeleter: FileDeleter,
        dispatcherProvider: DispatcherProvider
    ): FaviconPersister {
        return FileBasedFaviconPersister(context, fileDeleter, dispatcherProvider)
    }

    @Provides
    fun webViewPreviewGenerator(): WebViewPreviewGenerator {
        return FileBasedWebViewPreviewGenerator()
    }

    @Provides
    fun domLoginDetector(settingsDataStore: SettingsDataStore): DOMLoginDetector {
        return JsLoginDetector(settingsDataStore)
    }

    @Provides
    fun domUrlExtractor(): DOMUrlExtractor {
        return JsUrlExtractor()
    }

    @Provides
    fun blobConverterInjector(): BlobConverterInjector {
        return BlobConverterInjectorJs()
    }

    @Provides
    fun navigationAwareLoginDetector(
        settingsDataStore: SettingsDataStore,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): NavigationAwareLoginDetector {
        return NextPageLoginDetection(settingsDataStore, appCoroutineScope)
    }

    @Provides
    fun downloadFileService(@Named("api") retrofit: Retrofit): DownloadFileService = retrofit.create(DownloadFileService::class.java)

    @Provides
    fun fileDownloader(
        dataUriDownloader: DataUriDownloader,
        networkFileDownloader: NetworkFileDownloader
    ): FileDownloader {
        return AndroidFileDownloader(dataUriDownloader, networkFileDownloader)
    }

    @Provides
    fun fireproofLoginDialogEventHandler(
        userEventsStore: UserEventsStore,
        pixel: Pixel,
        fireproofWebsiteRepository: FireproofWebsiteRepository,
        appSettingsPreferencesStore: SettingsDataStore,
        variantManager: VariantManager,
        dispatchers: DispatcherProvider
    ): FireproofDialogsEventHandler {
        return BrowserTabFireproofDialogsEventHandler(
            userEventsStore,
            pixel,
            fireproofWebsiteRepository,
            appSettingsPreferencesStore,
            variantManager,
            dispatchers
        )
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun thirdPartyCookieManager(
        cookieManager: CookieManager,
        authCookiesAllowedDomainsRepository: AuthCookiesAllowedDomainsRepository
    ): ThirdPartyCookieManager {
        return AppThirdPartyCookieManager(cookieManager, authCookiesAllowedDomainsRepository)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    @IntoSet
    fun serviceWorkerLifecycleObserver(serviceWorkerLifecycleObserver: ServiceWorkerLifecycleObserver): LifecycleObserver =
        serviceWorkerLifecycleObserver
}
