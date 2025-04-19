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

import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.duckduckgo.app.usage.search.SearchCountDao
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.ui.DuckDuckGoTheme
import com.duckduckgo.common.ui.store.ThemingDataStore
import com.duckduckgo.savedsites.api.SavedSitesRepository
import java.util.Date

class AndroidUserBrowserProperties(
    private val themingDataStore: ThemingDataStore,
    private val savedSitesRepository: SavedSitesRepository,
    private val appInstallStore: AppInstallStore,
    private val widgetCapabilities: WidgetCapabilities,
    private val emailManager: EmailManager,
    private val searchCountDao: SearchCountDao,
    private val appDaysUsedRepository: AppDaysUsedRepository,
) : UserBrowserProperties {
    override fun appTheme(): DuckDuckGoTheme {
        return themingDataStore.theme
    }

    override suspend fun bookmarks(): Long {
        return savedSitesRepository.bookmarksCount()
    }

    override suspend fun favorites(): Long {
        return savedSitesRepository.favoritesCount()
    }

    override fun daysSinceInstalled(): Long {
        return appInstallStore.daysInstalled()
    }

    override suspend fun daysUsedSince(since: Date): Long {
        return appDaysUsedRepository.getNumberOfDaysAppUsedSinceDate(since)
    }

    override fun defaultBrowser(): Boolean {
        return appInstallStore.defaultBrowser
    }

    override fun emailEnabled(): Boolean {
        return emailManager.isSignedIn()
    }

    override fun searchCount(): Long {
        return searchCountDao.getSearchesMade()
    }

    override fun widgetAdded(): Boolean {
        return widgetCapabilities.hasInstalledWidgets
    }
}
