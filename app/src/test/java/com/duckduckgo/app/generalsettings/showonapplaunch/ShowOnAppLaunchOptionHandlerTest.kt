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

            assert(tabs.isEmpty())
        }
    }

    @Test
    fun whenOptionIsNewTabPageOpenedThenNewTabPageIsAdded() = runTest {
        fakeDataStore.setShowOnAppLaunchOption(NewTabPage)

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assert(tabs.size == 1)
            assert(tabs.last().url == "")
        }
    }

    @Test
    fun whenOptionIsSpecificUrlOpenedThenSpecificUrlTabIsAdded() = runTest {
        fakeDataStore.setShowOnAppLaunchOption(SpecificPage("https://example.com"))

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assert(tabs.size == 1)
            assert(tabs.last().url == "https://example.com")
        }
    }

    @Test
    fun whenOptionIsSpecificUrlOpenedAndTabAlreadyAddedThenTabIsSelected() = runTest {
        fakeDataStore.setShowOnAppLaunchOption(SpecificPage("https://example.com"))
        fakeTabRepository.add("https://example.com")

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assert(tabs.size == 1)
            assert(tabs.last().url == "https://example.com")
        }
    }

    @Test
    fun whenOptionIsSpecificUrlIsHttpAndHttpsTabAlreadyAddedThenTabIsSelected() = runTest {
        fakeDataStore.setShowOnAppLaunchOption(SpecificPage("http://example.com"))
        fakeTabRepository.add("https://example.com")

        testee.handleAppLaunchOption()

        fakeTabRepository.flowTabs.test {
            val tabs = awaitItem()
            awaitComplete()

            assert(tabs.size == 1)
            assert(tabs.last().url == "https://example.com")
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
            return tabs.values.firstOrNull { it == url }
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
