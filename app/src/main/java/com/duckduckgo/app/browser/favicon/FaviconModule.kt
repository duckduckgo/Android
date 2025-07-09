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

package com.duckduckgo.app.browser.favicon

import android.content.Context
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.sync.api.favicons.FaviconsFetchingStore
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
class FaviconModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun faviconManager(
        faviconPersister: FaviconPersister,
        bookmarksDao: SavedSitesEntitiesDao,
        fireproofWebsiteRepository: FireproofWebsiteRepository,
        savedSitesRepository: SavedSitesRepository,
        faviconDownloader: FaviconDownloader,
        dispatcherProvider: DispatcherProvider,
        autofillStore: AutofillStore,
        faviconsFetchingStore: FaviconsFetchingStore,
        context: Context,
    ): FaviconManager {
        return DuckDuckGoFaviconManager(
            faviconPersister,
            bookmarksDao,
            fireproofWebsiteRepository,
            savedSitesRepository,
            faviconDownloader,
            dispatcherProvider,
            autofillStore,
            faviconsFetchingStore,
            context,
        )
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun faviconDownloader(
        context: Context,
        dispatcherProvider: DispatcherProvider,
        androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    ): FaviconDownloader {
        return GlideFaviconDownloader(context, dispatcherProvider, androidBrowserConfigFeature)
    }
}
