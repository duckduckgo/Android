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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.WorkManager
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.browser.*
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.browser.addtohome.AddToHomeSystemCapabilityDetector
import com.duckduckgo.app.browser.applinks.ExternalAppIntentFlagsFeature
import com.duckduckgo.app.browser.certificates.rootstore.TrustedCertificateStore
import com.duckduckgo.app.browser.cookies.AppThirdPartyCookieManager
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.browser.cookies.db.AuthCookiesAllowedDomainsRepository
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserObserver
import com.duckduckgo.app.browser.downloader.*
import com.duckduckgo.app.browser.duckchat.AIChatQueryDetectionFeature
import com.duckduckgo.app.browser.favicon.FaviconPersister
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.httperrors.HttpCodeSiteErrorHandler
import com.duckduckgo.app.browser.httperrors.HttpCodeSiteErrorHandlerImpl
import com.duckduckgo.app.browser.httperrors.StringSiteErrorHandler
import com.duckduckgo.app.browser.httperrors.StringSiteErrorHandlerImpl
import com.duckduckgo.app.browser.logindetection.*
import com.duckduckgo.app.browser.pageloadpixel.PageLoadedPixelDao
import com.duckduckgo.app.browser.pageloadpixel.firstpaint.PagePaintedPixelDao
import com.duckduckgo.app.browser.session.WebViewSessionInMemoryStorage
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.browser.tabpreview.FileBasedPdfPreviewGenerator
import com.duckduckgo.app.browser.tabpreview.FileBasedWebViewPreviewGenerator
import com.duckduckgo.app.browser.tabpreview.FileBasedWebViewPreviewPersister
import com.duckduckgo.app.browser.tabpreview.PdfPreviewGenerator
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewGenerator
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.browser.urlextraction.DOMUrlExtractor
import com.duckduckgo.app.browser.urlextraction.JsUrlExtractor
import com.duckduckgo.app.browser.urlextraction.UrlExtractingWebViewClient
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockerWebViewIntegration
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.fire.*
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.referral.AppReferrerDataStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.surrogates.ResourceSurrogates
import com.duckduckgo.app.tabs.ui.GridViewColumnCalculator
import com.duckduckgo.app.trackerdetection.CloakedCnameDetector
import com.duckduckgo.app.trackerdetection.TrackerDetector
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.cookies.api.ThirdPartyCookieNames
import com.duckduckgo.customtabs.api.CustomTabDetector
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.impl.AndroidFileDownloader
import com.duckduckgo.downloads.impl.DataUriDownloader
import com.duckduckgo.downloads.impl.FileDownloadCallback
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.httpsupgrade.api.HttpsUpgrader
import com.duckduckgo.privacy.config.api.AmpLinks
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.TrackingParameters
import com.duckduckgo.request.filterer.api.RequestFilterer
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.user.agent.api.UserAgentProvider
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import javax.inject.Named
import javax.inject.Qualifier

@Module
class BrowserModule {

    @Provides
    fun duckDuckGoRequestRewriter(
        urlDetector: DuckDuckGoUrlDetector,
        statisticsStore: StatisticsDataStore,
        variantManager: VariantManager,
        appReferrerDataStore: AppReferrerDataStore,
        duckChat: DuckChat,
        androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    ): RequestRewriter {
        return DuckDuckGoRequestRewriter(urlDetector, statisticsStore, variantManager, appReferrerDataStore, duckChat, androidBrowserConfigFeature)
    }

    @Provides
    fun urlExtractingWebViewClient(
        webViewHttpAuthStore: WebViewHttpAuthStore,
        trustedCertificateStore: TrustedCertificateStore,
        requestInterceptor: RequestInterceptor,
        cookieManagerProvider: CookieManagerProvider,
        thirdPartyCookieManager: ThirdPartyCookieManager,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        urlExtractor: DOMUrlExtractor,
    ): UrlExtractingWebViewClient {
        return UrlExtractingWebViewClient(
            webViewHttpAuthStore,
            trustedCertificateStore,
            requestInterceptor,
            cookieManagerProvider,
            thirdPartyCookieManager,
            appCoroutineScope,
            dispatcherProvider,
            urlExtractor,
        )
    }

    @Provides
    fun webViewLongPressHandler(
        context: Context,
        pixel: Pixel,
        customTabDetector: CustomTabDetector,
    ): LongPressHandler {
        return WebViewLongPressHandler(context, pixel, customTabDetector)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    @IntoSet
    fun defaultBrowserObserver(
        defaultBrowserDetector: DefaultBrowserDetector,
        appInstallStore: AppInstallStore,
        pixel: Pixel,
    ): MainProcessLifecycleObserver {
        return DefaultBrowserObserver(defaultBrowserDetector, appInstallStore, pixel)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun webViewSessionStorage(): WebViewSessionStorage = WebViewSessionInMemoryStorage()

    @Provides
    fun clipboardManager(context: Context): ClipboardManager {
        return context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    @Provides
    fun addToHomeCapabilityDetector(context: Context): AddToHomeCapabilityDetector {
        return AddToHomeSystemCapabilityDetector(context)
    }

    @Provides
    fun specialUrlDetector(
        packageManager: PackageManager,
        ampLinks: AmpLinks,
        trackingParameters: TrackingParameters,
        subscriptions: Subscriptions,
        externalAppIntentFlagsFeature: ExternalAppIntentFlagsFeature,
        duckPlayer: DuckPlayer,
        duckChat: DuckChat,
        aiChatQueryDetectionFeature: AIChatQueryDetectionFeature,
        androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    ): SpecialUrlDetector = SpecialUrlDetectorImpl(
        packageManager,
        ampLinks,
        trackingParameters,
        subscriptions,
        externalAppIntentFlagsFeature,
        duckPlayer,
        duckChat,
        aiChatQueryDetectionFeature,
        androidBrowserConfigFeature,
    )

    @Provides
    fun webViewRequestInterceptor(
        resourceSurrogates: ResourceSurrogates,
        trackerDetector: TrackerDetector,
        httpsUpgrader: HttpsUpgrader,
        privacyProtectionCountDao: PrivacyProtectionCountDao,
        gpc: Gpc,
        userAgentProvider: UserAgentProvider,
        adClickManager: AdClickManager,
        cloakedCnameDetector: CloakedCnameDetector,
        requestFilterer: RequestFilterer,
        duckPlayer: DuckPlayer,
        maliciousSiteBlockerWebViewIntegration: MaliciousSiteBlockerWebViewIntegration,
        androidBrowserConfigFeature: AndroidBrowserConfigFeature,
        dispatchers: DispatcherProvider,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        @IsMainProcess isMainProcess: Boolean,
    ): RequestInterceptor =
        WebViewRequestInterceptor(
            resourceSurrogates,
            trackerDetector,
            httpsUpgrader,
            privacyProtectionCountDao,
            gpc,
            userAgentProvider,
            adClickManager,
            cloakedCnameDetector,
            requestFilterer,
            duckPlayer,
            maliciousSiteBlockerWebViewIntegration,
            dispatchers,
            androidBrowserConfigFeature,
            appCoroutineScope,
            isMainProcess,
        )

    @Provides
    @Named("webViewDbLocator")
    fun webViewDatabaseLocator(context: Context): DatabaseLocator = WebViewDatabaseLocator(context)

    @Provides
    @Named("authDbLocator")
    fun authDatabaseLocator(context: Context): DatabaseLocator = AuthDatabaseLocator(context)

    @Provides
    fun databaseCleanerHelper(): DatabaseCleaner = DatabaseCleanerHelper()

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun gridViewColumnCalculator(context: Context): GridViewColumnCalculator {
        return GridViewColumnCalculator(context)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun webViewPreviewPersister(
        context: Context,
        fileDeleter: FileDeleter,
        dispatchers: DispatcherProvider,
    ): WebViewPreviewPersister {
        return FileBasedWebViewPreviewPersister(context, fileDeleter, dispatchers)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun faviconPersister(
        context: Context,
        fileDeleter: FileDeleter,
        dispatcherProvider: DispatcherProvider,
        androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    ): FaviconPersister {
        return FileBasedFaviconPersister(context, fileDeleter, androidBrowserConfigFeature, dispatcherProvider)
    }

    @Provides
    fun webViewPreviewGenerator(dispatchers: DispatcherProvider): WebViewPreviewGenerator {
        return FileBasedWebViewPreviewGenerator(dispatchers = dispatchers)
    }

    @Provides
    fun pdfPreviewGenerator(
        context: Context,
        dispatchers: DispatcherProvider,
    ): PdfPreviewGenerator {
        return FileBasedPdfPreviewGenerator(context = context, dispatchers = dispatchers)
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
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
    ): NavigationAwareLoginDetector {
        return NextPageLoginDetection(settingsDataStore, appCoroutineScope)
    }

    @Provides
    fun fileDownloader(
        dataUriDownloader: DataUriDownloader,
        callback: FileDownloadCallback,
        workManager: WorkManager,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
    ): FileDownloader {
        return AndroidFileDownloader(dataUriDownloader, callback, workManager, appCoroutineScope, dispatcherProvider)
    }

    @Provides
    fun fireproofLoginDialogEventHandler(
        userEventsStore: UserEventsStore,
        pixel: Pixel,
        fireproofWebsiteRepository: FireproofWebsiteRepository,
        appSettingsPreferencesStore: SettingsDataStore,
        dispatchers: DispatcherProvider,
    ): FireproofDialogsEventHandler {
        return BrowserTabFireproofDialogsEventHandler(
            userEventsStore,
            pixel,
            fireproofWebsiteRepository,
            appSettingsPreferencesStore,
            dispatchers,
        )
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun thirdPartyCookieManager(
        cookieManagerProvider: CookieManagerProvider,
        authCookiesAllowedDomainsRepository: AuthCookiesAllowedDomainsRepository,
        thirdPartyCookieNames: ThirdPartyCookieNames,
    ): ThirdPartyCookieManager {
        return AppThirdPartyCookieManager(cookieManagerProvider, authCookiesAllowedDomainsRepository, thirdPartyCookieNames)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providePageLoadedPixelDao(appDatabase: AppDatabase): PageLoadedPixelDao {
        return appDatabase.pageLoadedPixelDao()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providePagePaintedPixelDao(appDatabase: AppDatabase): PagePaintedPixelDao {
        return appDatabase.pagePaintedPixelDao()
    }

    private val Context.indonesiaNewTabSectionDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "indonesia_new_tab_section_store",
    )

    @Provides
    @SingleInstanceIn(AppScope::class)
    @IndonesiaNewTabSection
    fun provideIndonesiaNewTabSectionDataStore(context: Context): DataStore<Preferences> {
        return context.indonesiaNewTabSectionDataStore
    }

    @Provides
    fun provideSiteErrorStringHandler(): StringSiteErrorHandler {
        return StringSiteErrorHandlerImpl()
    }

    @Provides
    fun provideSiteErrorCodeHandler(): HttpCodeSiteErrorHandler {
        return HttpCodeSiteErrorHandlerImpl()
    }
}

@Qualifier
annotation class IndonesiaNewTabSection
