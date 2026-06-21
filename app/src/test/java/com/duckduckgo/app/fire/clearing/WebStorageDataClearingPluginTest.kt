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

import android.webkit.WebStorage
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DeleteBrowsingData
import com.duckduckgo.app.browser.mode.WebStorageProvider
import com.duckduckgo.app.fire.SiteDataCleaner
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.fire.store.TabVisitedSitesRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WebStorageDataClearingPluginTest {
    @get:Rule val coroutineRule = CoroutineTestRule()
    private val fireStorage: WebStorage = mock()
    private val webStorageProvider: WebStorageProvider = mock()
    private val tabVisitedSitesRepository: TabVisitedSitesRepository = mock()
    private val siteDataCleaner: SiteDataCleaner = mock()
    private val webViewCapabilityChecker: WebViewCapabilityChecker = mock()
    private val fireproofWebsiteRepository: FireproofWebsiteRepository = mock()
    private val duckAiHostProvider: DuckAiHostProvider = mock { on { getHost() } doReturn "duck.ai" }
    private val testee = WebStorageDataClearingPlugin(
        webStorageProvider,
        tabVisitedSitesRepository,
        siteDataCleaner,
        webViewCapabilityChecker,
        fireproofWebsiteRepository,
        duckAiHostProvider,
        coroutineRule.testDispatcherProvider,
    )

    @Test fun whenFireAllForModeThenDeletesFireWebStorage() = runTest {
        whenever(webStorageProvider.forMode(BrowserMode.FIRE)).thenReturn(fireStorage)
        testee.onClearData(setOf(ClearableData.BrowserData.AllForMode(BrowserMode.FIRE)))
        verify(fireStorage).deleteAllData()
    }

    @Test fun whenBrowserDataAllThenDeletesFireWebStorage() = runTest {
        whenever(webStorageProvider.forMode(BrowserMode.FIRE)).thenReturn(fireStorage)
        testee.onClearData(setOf(ClearableData.BrowserData.All))
        verify(fireStorage).deleteAllData()
    }

    @Test fun whenRegularAllForModeThenDoesNothing() = runTest {
        testee.onClearData(setOf(ClearableData.BrowserData.AllForMode(BrowserMode.REGULAR)))
        verify(fireStorage, never()).deleteAllData()
    }

    @Test fun whenFireSingleForModeThenClearsVisitedDomainsExceptDdgAndFireproofed() = runTest {
        whenever(webViewCapabilityChecker.isSupported(DeleteBrowsingData)).thenReturn(true)
        whenever(webStorageProvider.forMode(BrowserMode.FIRE)).thenReturn(fireStorage)
        // Mirror ClearPersonalDataActionTest's fireproof stubbing; toTldPlusOne() resolves these to themselves.
        whenever(fireproofWebsiteRepository.fireproofWebsitesSync())
            .thenReturn(listOf(FireproofWebsiteEntity(domain = "fireproofed.com")))
        whenever(tabVisitedSitesRepository.getVisitedSites("tab1"))
            .thenReturn(setOf("example.com", "duck.ai", "fireproofed.com"))
        testee.onClearData(setOf(ClearableData.BrowserData.SingleForMode("tab1", BrowserMode.FIRE)))
        verify(siteDataCleaner).deleteSiteData(fireStorage, "example.com")
        verify(siteDataCleaner, never()).deleteSiteData(fireStorage, "duck.ai") // DDG excluded
        verify(siteDataCleaner, never()).deleteSiteData(fireStorage, "fireproofed.com") // fireproofed excluded
    }

    @Test fun whenSingleForModeAndCapabilityUnsupportedThenNoOp() = runTest {
        whenever(webViewCapabilityChecker.isSupported(DeleteBrowsingData)).thenReturn(false)
        testee.onClearData(setOf(ClearableData.BrowserData.SingleForMode("tab1", BrowserMode.FIRE)))
        verify(siteDataCleaner, never()).deleteSiteData(any(), any())
    }
}
