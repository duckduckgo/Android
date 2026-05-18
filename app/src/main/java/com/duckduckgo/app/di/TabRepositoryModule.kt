/*
 * Copyright (c) 2026 DuckDuckGo
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

import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.fire.store.TabVisitedSitesRepository
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.tabs.model.TabAtomicOperations
import com.duckduckgo.app.tabs.model.TabDataRepository
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.store.TabSwitcherDataStore
import com.duckduckgo.browsermode.api.FireMode
import com.duckduckgo.browsermode.api.RegularMode
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStatePublisher
//noinspection NoImplImportsInAppModule
import com.duckduckgo.duckchat.impl.store.DuckChatContextualDataStore
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@ContributesTo(AppScope::class)
@Module
abstract class TabRepositoryModule {

    @Binds
    @RegularMode
    abstract fun bindRegularTabRepository(@RegularMode impl: TabDataRepository): TabRepository

    @Binds
    @FireMode
    abstract fun bindFireTabRepository(@FireMode impl: TabDataRepository): TabRepository

    @Binds
    abstract fun bindUnqualifiedTabRepository(@RegularMode impl: TabRepository): TabRepository

    @Binds
    abstract fun bindTabAtomicOperations(@RegularMode impl: TabDataRepository): TabAtomicOperations

    companion object {

        @Provides
        @SingleInstanceIn(AppScope::class)
        @RegularMode
        fun provideRegularTabRepository(
            @RegularMode tabsDao: TabsDao,
            siteFactory: SiteFactory,
            webViewPreviewPersister: WebViewPreviewPersister,
            faviconManager: FaviconManager,
            tabSwitcherDataStore: TabSwitcherDataStore,
            timeProvider: CurrentTimeProvider,
            @AppCoroutineScope appCoroutineScope: CoroutineScope,
            dispatchers: DispatcherProvider,
            adClickManager: AdClickManager,
            webViewSessionStorage: WebViewSessionStorage,
            tabManagerFeatureFlags: TabManagerFeatureFlags,
            duckChatContextualDataStore: DuckChatContextualDataStore,
            tabVisitedSitesRepository: TabVisitedSitesRepository,
            nativeInputStatePublisher: NativeInputStatePublisher,
        ): TabDataRepository = TabDataRepository(
            tabsDao = tabsDao,
            siteFactory = siteFactory,
            webViewPreviewPersister = webViewPreviewPersister,
            faviconManager = faviconManager,
            tabSwitcherDataStore = tabSwitcherDataStore,
            timeProvider = timeProvider,
            appCoroutineScope = appCoroutineScope,
            dispatchers = dispatchers,
            adClickManager = adClickManager,
            webViewSessionStorage = webViewSessionStorage,
            tabManagerFeatureFlags = tabManagerFeatureFlags,
            duckChatContextualDataStore = duckChatContextualDataStore,
            tabVisitedSitesRepository = tabVisitedSitesRepository,
            nativeInputStatePublisher = nativeInputStatePublisher,
        )

        @Provides
        @SingleInstanceIn(AppScope::class)
        @FireMode
        fun provideFireTabRepository(
            @FireMode tabsDao: TabsDao,
            siteFactory: SiteFactory,
            webViewPreviewPersister: WebViewPreviewPersister,
            faviconManager: FaviconManager,
            tabSwitcherDataStore: TabSwitcherDataStore,
            timeProvider: CurrentTimeProvider,
            @AppCoroutineScope appCoroutineScope: CoroutineScope,
            dispatchers: DispatcherProvider,
            adClickManager: AdClickManager,
            webViewSessionStorage: WebViewSessionStorage,
            tabManagerFeatureFlags: TabManagerFeatureFlags,
            duckChatContextualDataStore: DuckChatContextualDataStore,
            tabVisitedSitesRepository: TabVisitedSitesRepository,
            nativeInputStatePublisher: NativeInputStatePublisher,
        ): TabDataRepository = TabDataRepository(
            tabsDao = tabsDao,
            siteFactory = siteFactory,
            webViewPreviewPersister = webViewPreviewPersister,
            faviconManager = faviconManager,
            tabSwitcherDataStore = tabSwitcherDataStore,
            timeProvider = timeProvider,
            appCoroutineScope = appCoroutineScope,
            dispatchers = dispatchers,
            adClickManager = adClickManager,
            webViewSessionStorage = webViewSessionStorage,
            tabManagerFeatureFlags = tabManagerFeatureFlags,
            duckChatContextualDataStore = duckChatContextualDataStore,
            tabVisitedSitesRepository = tabVisitedSitesRepository,
            nativeInputStatePublisher = nativeInputStatePublisher,
        )
    }
}
