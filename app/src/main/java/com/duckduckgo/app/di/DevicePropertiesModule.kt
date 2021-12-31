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

package com.duckduckgo.app.di

import android.content.Context
import com.duckduckgo.app.bookmarks.model.BookmarksRepository
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.store.AndroidAppProperties
import com.duckduckgo.app.global.store.AndroidDeviceProperties
import com.duckduckgo.app.global.store.AndroidUserBrowserProperties
import com.duckduckgo.app.playstore.PlayStoreUtils
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.duckduckgo.app.usage.search.SearchCountDao
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.browser.api.AppProperties
import com.duckduckgo.browser.api.DeviceProperties
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.ui.store.ThemingDataStore
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(AppScope::class)
class DevicePropertiesModule {
    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesDeviceProperties(appContext: Context): DeviceProperties {
        return AndroidDeviceProperties(appContext)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesAppProperties(
        variantManager: VariantManager,
        playStoreUtils: PlayStoreUtils,
        statisticsStore: StatisticsDataStore
    ): AppProperties {
        return AndroidAppProperties(
            variantManager,
            playStoreUtils,
            statisticsStore
        )
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesUserBrowserProperties(
        themingDataStore: ThemingDataStore,
        bookmarksRepository: BookmarksRepository,
        favoritesRepository: FavoritesRepository,
        appInstallStore: AppInstallStore,
        widgetCapabilities: WidgetCapabilities,
        emailManager: EmailManager,
        searchCountDao: SearchCountDao,
        appDaysUsedRepository: AppDaysUsedRepository
    ): UserBrowserProperties {
        return AndroidUserBrowserProperties(
            themingDataStore,
            bookmarksRepository,
            favoritesRepository,
            appInstallStore,
            widgetCapabilities,
            emailManager,
            searchCountDao,
            appDaysUsedRepository
        )
    }
}