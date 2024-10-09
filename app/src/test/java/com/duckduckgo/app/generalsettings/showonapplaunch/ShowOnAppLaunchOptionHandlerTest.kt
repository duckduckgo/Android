/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.generalsettings.showonapplaunch

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.app.generalsettings.showonapplaunch.store.FakeShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.model.TabSwitcherData
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShowOnAppLaunchOptionHandlerTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var fakeDataStore: ShowOnAppLaunchOptionDataStore
    private lateinit var fakeTabRepository: TabRepository
    private lateinit var testee: ShowOnAppLaunchOptionHandler

    @Before
    fun setup() {
        fakeDataStore = FakeShowOnAppLaunchOptionDataStore()
        fakeTabRepository = FakeTabRepository()
        testee = ShowOnAppLaunchOptionHandler(fakeDataStore, fakeTabRepository)
    }

    @Test
    fun whenOptionIsLastTabOpenedThenNoTabIsAdded() = runTest {
        fakeDataStore.setShowOnAppLaunchOption(LastOpenedTab)

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assertTrue(tabs.isEmpty())
        }
    }

    @Test
    fun whenOptionIsNewTabPageOpenedThenNewTabPageIsAdded() = runTest {
        fakeDataStore.setShowOnAppLaunchOption(NewTabPage)

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assertTrue(tabs.size == 1)
            assertTrue(tabs.last().url == "")
        }
    }

    @Test
    fun whenOptionIsSpecificUrlOpenedThenSpecificUrlTabIsAdded() = runTest {
        val url = "https://example.com"

        fakeDataStore.setShowOnAppLaunchOption(SpecificPage(url))

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assertTrue(tabs.size == 1)
            assertTrue(tabs.last().url == url)
        }
    }

    @Test
    fun whenOptionIsSpecificUrlOpenedAndTabAlreadyAddedThenTabIsSelected() = runTest {
        val url = "https://example.com"

        fakeDataStore.setShowOnAppLaunchOption(SpecificPage(url))
        fakeTabRepository.add(url)

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assertTrue(tabs.size == 1)
            assertTrue(tabs.last().url == url)
        }
    }

    @Test
    fun whenOptionIsSpecificUrlIsHttpAndHttpsTabAlreadyAddedThenTabIsNotAdded() = runTest {
        fakeDataStore.setShowOnAppLaunchOption(SpecificPage("http://example.com"))
        fakeTabRepository.add("https://example.com")

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assertTrue(tabs.size == 1)
            assertTrue(tabs.last().url == "https://example.com")
        }
    }

    @Test
    fun whenOptionIsSpecificUrlWithNoForwardSlashSuffixAndTabAlreadyAddedWithForwardSlashSuffixThenTabIsNotAdded() = runTest {
        fakeDataStore.setShowOnAppLaunchOption(SpecificPage("example.com"))
        fakeTabRepository.add("example.com/")

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assertTrue(tabs.size == 1)
            assertTrue(tabs.last().url == "example.com/")
        }
    }

    @Test
    fun whenOptionIsSpecificUrlWithAForwardSlashSuffixAndTabAlreadyAddedWithForwardSlashSuffixThenTabIsNotAdded() = runTest {
        val urlWithForwardSlashSuffix = "example.com/"

        fakeDataStore.setShowOnAppLaunchOption(SpecificPage(urlWithForwardSlashSuffix))
        fakeTabRepository.add(urlWithForwardSlashSuffix)

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assertTrue(tabs.size == 1)
            assertTrue(tabs.last().url == urlWithForwardSlashSuffix)
        }
    }

    @Test
    fun whenOptionIsSpecificUrlWithDomainOnlyAndTabAlreadyAddedWithSchemeAndDomainThenTabIsNotAdded() = runTest {
        fakeDataStore.setShowOnAppLaunchOption(SpecificPage("example.com"))
        fakeTabRepository.add("https://www.example.com/")

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assertTrue(tabs.size == 1)
            assertTrue(tabs.last().url == "https://www.example.com/")
        }
    }

    @Test
    fun whenOptionIsSpecificUrlWithDomainNameOnlyAndNotAddedThenTabIsAdded() = runTest {
        val domainName = "example.com"

        fakeDataStore.setShowOnAppLaunchOption(SpecificPage(domainName))

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assertTrue(tabs.size == 1)
            assertTrue(tabs.last().url == domainName)
        }
    }

    @Test
    fun whenOptionIsSpecificUrlWithQueryStringThenTabIsAdded() = runTest {
        val queryUrl = "https://example.com?query=1"

        fakeDataStore.setShowOnAppLaunchOption(SpecificPage(queryUrl))

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assertTrue(tabs.size == 1)
            assertTrue(tabs.last().url == queryUrl)
        }
    }

    @Test
    fun whenOptionIsSpecificUrlWithFragmentThenTabIsAdded() = runTest {
        val fragmentUrl = "https://example.com#fragment"

        fakeDataStore.setShowOnAppLaunchOption(SpecificPage(fragmentUrl))

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assertTrue(tabs.size == 1)
            assertTrue(tabs.last().url == fragmentUrl)
        }
    }

    @Test
    fun whenOptionIsSpecificUrlWithFragmentAndIsAddedThenTabIsNotAdded() = runTest {
        val fragmentUrl = "https://example.com#fragment"

        fakeDataStore.setShowOnAppLaunchOption(SpecificPage(fragmentUrl))
        fakeTabRepository.add(fragmentUrl)

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assertTrue(tabs.size == 1)
            assertTrue(tabs.last().url == fragmentUrl)
        }
    }

    @Test
    fun whenOptionIsSpecificUrlWithQueryStringAndFragmentThenTabIsAdded() = runTest {
        val queryFragmentUrl = "https://example.com?query=1#fragment"

        fakeDataStore.setShowOnAppLaunchOption(SpecificPage(queryFragmentUrl))

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assertTrue(tabs.size == 1)
            assertTrue(tabs.last().url == queryFragmentUrl)
        }
    }

    @Test
    fun whenOptionIsSpecificUrlWithQueryStringAndFragmentAndIsAddedThenTabIsNotAdded() = runTest {
        val queryFragmentUrl = "https://example.com?query=1#fragment"

        fakeDataStore.setShowOnAppLaunchOption(SpecificPage(queryFragmentUrl))
        fakeTabRepository.add(queryFragmentUrl)

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assertTrue(tabs.size == 1)
            assertTrue(tabs.last().url == queryFragmentUrl)
        }
    }

    @Test
    fun whenOptionIsSpecificUrlWithNonHttpOrHttpsProtocolAndNotAddedThenTabIsAdded() = runTest {
        val ftpUrl = "ftp://example.com"

        fakeDataStore.setShowOnAppLaunchOption(SpecificPage(ftpUrl))

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assertTrue(tabs.size == 1)
            assertTrue(tabs.last().url == ftpUrl)
        }
    }

    @Test
    fun whenOptionIsSpecificUrlWithNonHttpOrHttpsProtocolAndAddedThenTabIsNotAdded() = runTest {
        val ftpUrl = "ftp://example.com"

        fakeDataStore.setShowOnAppLaunchOption(SpecificPage(ftpUrl))
        fakeTabRepository.add(ftpUrl)

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assertTrue(tabs.size == 1)
            assertTrue(tabs.last().url == ftpUrl)
        }
    }

    private class FakeTabRepository : TabRepository {

        private val tabs = mutableMapOf<Int, String>()

        override suspend fun select(tabId: String) = Unit

        override suspend fun add(
            url: String?,
            skipHome: Boolean
        ): String {
            tabs[tabs.size + 1] = url ?: ""
            return tabs.size.toString()
        }

        override suspend fun getTabId(url: String): String? {
            return tabs.values.firstOrNull { it.contains(url) }
        }

        override val flowTabs: Flow<List<TabEntity>> = flowOf(tabs).map {
            it.map { (id, url) -> TabEntity(tabId = id.toString(), url = url, position = id) }
        }

        override val liveTabs: LiveData<List<TabEntity>>
            get() = TODO("Not yet implemented")
        override val childClosedTabs: SharedFlow<String>
            get() = TODO("Not yet implemented")
        override val flowDeletableTabs: Flow<List<TabEntity>>
            get() = TODO("Not yet implemented")
        override val liveSelectedTab: LiveData<TabEntity>
            get() = TODO("Not yet implemented")
        override val tabSwitcherData: Flow<TabSwitcherData>
            get() = TODO("Not yet implemented")

        override suspend fun addDefaultTab(): String {
            TODO("Not yet implemented")
        }

        override suspend fun addFromSourceTab(
            url: String?,
            skipHome: Boolean,
            sourceTabId: String
        ): String {
            TODO("Not yet implemented")
        }

        override suspend fun addNewTabAfterExistingTab(
            url: String?,
            tabId: String
        ) {
            TODO("Not yet implemented")
        }

        override suspend fun update(
            tabId: String,
            site: Site?
        ) {
            TODO("Not yet implemented")
        }

        override suspend fun updateTabPosition(
            from: Int,
            to: Int
        ) {
            TODO("Not yet implemented")
        }

        override fun retrieveSiteData(tabId: String): MutableLiveData<Site> {
            TODO("Not yet implemented")
        }

        override suspend fun delete(tab: TabEntity) {
            TODO("Not yet implemented")
        }

        override suspend fun markDeletable(tab: TabEntity) {
            TODO("Not yet implemented")
        }

        override suspend fun undoDeletable(tab: TabEntity) {
            TODO("Not yet implemented")
        }

        override suspend fun purgeDeletableTabs() {
            TODO("Not yet implemented")
        }

        override suspend fun getDeletableTabIds(): List<String> {
            TODO("Not yet implemented")
        }

        override suspend fun deleteTabAndSelectSource(tabId: String) {
            TODO("Not yet implemented")
        }

        override suspend fun deleteAll() {
            TODO("Not yet implemented")
        }

        override suspend fun getSelectedTab(): TabEntity? {
            TODO("Not yet implemented")
        }

        override fun updateTabPreviewImage(
            tabId: String,
            fileName: String?
        ) {
            TODO("Not yet implemented")
        }

        override fun updateTabFavicon(
            tabId: String,
            fileName: String?
        ) {
            TODO("Not yet implemented")
        }

        override suspend fun selectByUrlOrNewTab(url: String) {
            TODO("Not yet implemented")
        }

        override suspend fun setIsUserNew(isUserNew: Boolean) {
            TODO("Not yet implemented")
        }

        override suspend fun setTabLayoutType(layoutType: LayoutType) {
            TODO("Not yet implemented")
        }
    }
}
