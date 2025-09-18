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

package com.duckduckgo.app.di

import android.content.Context
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.browser.WebDataManager
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.fire.AndroidAppCacheClearer
import com.duckduckgo.app.fire.AppCacheClearer
import com.duckduckgo.app.fire.BackgroundTimeKeeper
import com.duckduckgo.app.fire.DataClearerForegroundAppRestartPixel
import com.duckduckgo.app.fire.DataClearerTimeKeeper
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.fire.model.AppCacheExclusionPlugin
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.global.view.ClearPersonalDataAction
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.location.data.LocationPermissionsDao
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.location.data.LocationPermissionsRepositoryImpl
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.EntityLookup
import com.duckduckgo.app.trackerdetection.TdsEntityLookup
import com.duckduckgo.app.trackerdetection.api.WebTrackersBlockedRepository
import com.duckduckgo.app.trackerdetection.db.TdsDomainEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsEntityDao
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.cookies.api.DuckDuckGoCookieManager
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupDataClearer
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import com.duckduckgo.sync.api.DeviceSyncState
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import dagger.multibindings.IntoSet

@Module
object PrivacyModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun entityLookup(
        entityDao: TdsEntityDao,
        domainEntityDao: TdsDomainEntityDao,
    ): EntityLookup =
        TdsEntityLookup(entityDao, domainEntityDao)

    @Provides
    fun clearDataAction(
        context: Context,
        dataManager: WebDataManager,
        clearingStore: UnsentForgetAllPixelStore,
        tabRepository: TabRepository,
        settingsDataStore: SettingsDataStore,
        cookieManager: DuckDuckGoCookieManager,
        appCacheClearer: AppCacheClearer,
        thirdPartyCookieManager: ThirdPartyCookieManager,
        adClickManager: AdClickManager,
        fireproofWebsiteRepository: FireproofWebsiteRepository,
        sitePermissionsManager: SitePermissionsManager,
        deviceSyncState: DeviceSyncState,
        savedSitesRepository: SavedSitesRepository,
        privacyProtectionsPopupDataClearer: PrivacyProtectionsPopupDataClearer,
        navigationHistory: NavigationHistory,
        dispatcherProvider: DispatcherProvider,
        webTrackingRepository: WebTrackersBlockedRepository,
    ): ClearDataAction {
        return ClearPersonalDataAction(
            context,
            dataManager,
            clearingStore,
            tabRepository,
            settingsDataStore,
            cookieManager,
            appCacheClearer,
            thirdPartyCookieManager,
            adClickManager,
            fireproofWebsiteRepository,
            sitePermissionsManager,
            deviceSyncState,
            savedSitesRepository,
            privacyProtectionsPopupDataClearer,
            navigationHistory,
            dispatcherProvider,
            webTrackingRepository,
        )
    }

    @Provides
    fun backgroundTimeKeeper(): BackgroundTimeKeeper {
        return DataClearerTimeKeeper()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    @IntoSet
    fun dataClearerForegroundAppRestartPixelObserver(
        dataClearerForegroundAppRestartPixel: DataClearerForegroundAppRestartPixel,
    ): MainProcessLifecycleObserver = dataClearerForegroundAppRestartPixel

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun appCacheCleaner(
        context: Context,
        fileDeleter: FileDeleter,
        exclusionPlugins: PluginPoint<AppCacheExclusionPlugin>,
    ): AppCacheClearer {
        return AndroidAppCacheClearer(context, fileDeleter, exclusionPlugins)
    }

    @Provides
    fun providesLocationPermissionsRepository(
        locationPermissionsDao: LocationPermissionsDao,
        faviconManager: Lazy<FaviconManager>,
        dispatchers: DispatcherProvider,
    ): LocationPermissionsRepository {
        return LocationPermissionsRepositoryImpl(locationPermissionsDao, faviconManager, dispatchers)
    }
}
