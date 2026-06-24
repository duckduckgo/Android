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

package com.duckduckgo.app.fire.clearing

import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DeleteBrowsingData
import com.duckduckgo.app.browser.mode.WebStorageProvider
import com.duckduckgo.app.fire.SiteDataCleaner
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.fire.store.TabVisitedSitesRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.toTldPlusOne
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.dataclearing.api.plugin.DataClearingPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.withContext
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

/**
 * Owns Fire site/web-storage clearing logic.
 */
@ContributesMultibinding(AppScope::class)
class WebStorageDataClearingPlugin @Inject constructor(
    private val webStorageProvider: WebStorageProvider,
    private val tabVisitedSitesRepository: TabVisitedSitesRepository,
    private val siteDataCleaner: SiteDataCleaner,
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val duckAiHostProvider: DuckAiHostProvider,
    private val dispatchers: DispatcherProvider,
) : DataClearingPlugin {

    override suspend fun onClearData(types: Set<ClearableData>) {
        types.forEach { type ->
            when (type) {
                is ClearableData.BrowserData.All -> performFullDelete(BrowserMode.FIRE)
                is ClearableData.BrowserData.AllForMode -> if (type.mode == BrowserMode.FIRE) performFullDelete(type.mode)
                is ClearableData.BrowserData.SingleForMode -> if (type.mode == BrowserMode.FIRE) performSiteDelete(type.tabId, type.mode)
                else -> { /* not handled */ }
            }
        }
    }

    private suspend fun performFullDelete(browserMode: BrowserMode) {
        withContext(dispatchers.main()) {
            logcat { "Clearing $browserMode web storage" }
            siteDataCleaner.deleteAllBrowsingData(webStorageProvider.forMode(browserMode))
        }
    }

    private suspend fun performSiteDelete(tabId: String, browserMode: BrowserMode) {
        if (!webViewCapabilityChecker.isSupported(DeleteBrowsingData)) return
        val fireproofDomains = withContext(dispatchers.io()) {
            fireproofWebsiteRepository.fireproofWebsitesSync().mapNotNull { it.domain.toTldPlusOne() }.toSet()
        }
        val domains = tabVisitedSitesRepository.getVisitedSites(tabId)
            .filterNot { it in duckDuckGoDomains || it in fireproofDomains }
        withContext(dispatchers.main()) {
            val webStorage = webStorageProvider.forMode(browserMode)
            domains.forEach { siteDataCleaner.deleteSiteData(webStorage, it) }
        }
    }

    private val duckDuckGoDomains: Set<String> by lazy {
        setOf("duckduckgo.com", duckAiHostProvider.getHost())
            .map { host -> "https://$host".toHttpUrlOrNull()?.topPrivateDomain() ?: host }
            .toSet()
    }
}
