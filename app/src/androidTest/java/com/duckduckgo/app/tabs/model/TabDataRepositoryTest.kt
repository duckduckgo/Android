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

@file:Suppress("RemoveExplicitTypeArguments")

package com.duckduckgo.app.tabs.model

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.blockingObserve
import com.duckduckgo.app.browser.certificates.BypassedSSLCertificatesRepository
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.model.SiteFactoryImpl
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.trackerdetection.EntityLookup
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.privacy.config.api.ContentBlocking
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

class TabDataRepositoryTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val mockDao: TabsDao = mock()

    private val daoDeletableTabs = Channel<List<TabEntity>>()

    @After
    fun after() {
        daoDeletableTabs.close()
    }

    @Test
    fun whenAddNewTabAfterExistingTabWithUrlWithNoHostThenUsesUrlAsTitle() = runTest {
        val testee = tabDataRepository()
        val badUrl = "//bad/url"
        testee.addNewTabAfterExistingTab(badUrl, "tabid")
        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).insertTabAtPosition(captor.capture())
        assertEquals(badUrl, captor.firstValue.url)
    }

    @Test
    fun whenTabAddDirectlyThenViewedIsTrue() = runTest {
        val testee = tabDataRepository()
        testee.add("http://www.example.com")

        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).addAndSelectTab(captor.capture())
        assertTrue(captor.firstValue.viewed)
    }

    @Test
    fun whenTabUpdatedAfterOpenInBackgroundThenViewedIsTrue() = runTest {
        val testee = tabDataRepository()
        testee.addNewTabAfterExistingTab("http://www.example.com", "tabid")
        testee.update("tabid", null)

        val captor = argumentCaptor<Boolean>()
        verify(mockDao).updateUrlAndTitle(any(), anyOrNull(), anyOrNull(), captor.capture())
        assertTrue(captor.firstValue)
    }

    @Test
    fun whenNewTabAddedAfterNonExistingTabThenTitleUrlPositionOfNewTabAreCorrectAndTabIsNotViewed() = runTest {
        val testee = tabDataRepository()
        testee.addNewTabAfterExistingTab("http://www.example.com", "tabid")

        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).insertTabAtPosition(captor.capture())
        assertNotNull(captor.firstValue.tabId)
        assertEquals(0, captor.firstValue.position)
        assertFalse(captor.firstValue.viewed)
    }

    @Test
    fun whenNewTabAddedAfterExistingTabThenTitleUrlPositionOfNewTabAreCorrect() = runTest {
        val testee = tabDataRepository()
        val tab = TabEntity("tabid", position = 3)
        whenever(mockDao.tab(any())).thenReturn(tab)

        testee.addNewTabAfterExistingTab("http://www.example.com", "tabid")

        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).insertTabAtPosition(captor.capture())
        assertNotNull(captor.firstValue.tabId)
        assertEquals("http://www.example.com", captor.firstValue.url)
        assertEquals("example.com", captor.firstValue.title)
        assertEquals(4, captor.firstValue.position)
    }

    @Test
    fun whenAddCalledThenTabAddedAndSelectedAndBlankSiteDataCreated() = runTest {
        val testee = tabDataRepository()
        val createdId = testee.add()
        verify(mockDao).addAndSelectTab(any())
        assertNotNull(testee.retrieveSiteData(createdId))
    }

    @Test
    fun whenAddCalledWithUrlThenTabAddedAndSelectedAndUrlSiteDataCreated() = runTest {
        val testee = tabDataRepository()
        val url = "http://example.com"
        val createdId = testee.add(url)
        verify(mockDao).addAndSelectTab(any())
        assertNotNull(testee.retrieveSiteData(createdId))
        assertEquals(url, testee.retrieveSiteData(createdId).value!!.url)
    }

    @Test
    fun whenDataDoesNotExistForTabThenRetrieveCreatesIt() {
        val testee = tabDataRepository()
        assertNotNull(testee.retrieveSiteData(TAB_ID))
    }

    @Test
    fun whenTabDeletedThenTabAndDataCleared() = runTest {
        val testee = tabDataRepository()
        val addedTabId = testee.add()
        val siteData = testee.retrieveSiteData(addedTabId)

        testee.delete(TabEntity(addedTabId, position = 0))

        verify(mockDao).deleteTabAndUpdateSelection(any())
        assertNotSame(siteData, testee.retrieveSiteData(addedTabId))
    }

    @Test
    fun whenAllDeletedThenTabAndDataCleared() = runTest {
        val testee = tabDataRepository()
        val addedTabId = testee.add()
        val siteData = testee.retrieveSiteData(addedTabId)

        testee.deleteAll()

        verify(mockDao).deleteAllTabs()
        assertNotSame(siteData, testee.retrieveSiteData(addedTabId))
    }

    @Test
    fun whenIdSelectedThenCurrentUpdated() = runTest {
        val testee = tabDataRepository()
        testee.select(TAB_ID)
        verify(mockDao).insertTabSelection(eq(TabSelectionEntity(tabId = TAB_ID)))
    }

    @Test
    fun whenAddingFirstTabPositionIsAlwaysZero() = runTest {
        val testee = tabDataRepository()
        whenever(mockDao.tabs()).thenReturn(emptyList())

        testee.add("http://www.example.com")

        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).addAndSelectTab(captor.capture())
        assertTrue(captor.firstValue.position == 0)
    }

    @Test
    fun whenAddingTabToExistingTabsPositionIsAlwaysIncreased() = runTest {
        val testee = tabDataRepository()
        val tab0 = TabEntity("tabid", position = 0)
        val existingTabs = listOf(tab0)

        whenever(mockDao.tabs()).thenReturn(existingTabs)

        testee.add("http://www.example.com")

        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).addAndSelectTab(captor.capture())
        assertTrue(captor.firstValue.position == 1)
    }

    @Test
    fun whenAddDefaultTabToExistingListOfTabsThenTabIsNotCreated() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        val testee = tabDataRepository(dao)

        testee.add("example.com")

        testee.addDefaultTab()

        testee.flowTabs.test {
            assertTrue(awaitItem().size == 1)
        }
    }

    @Test
    fun whenAddDefaultTabToEmptyTabsThenTabIsCreated() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        val testee = tabDataRepository(dao)

        testee.addDefaultTab()

        testee.flowTabs.test {
            assertTrue(awaitItem().size == 1)
        }
    }

    @Test
    fun whenSelectByUrlOrNewTabIfUrlAlreadyExistedInATabThenSelectTheTab() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        dao.insertTab(TabEntity(tabId = "id", url = "http://www.example.com", skipHome = false, viewed = true, position = 0))
        val testee = tabDataRepository(dao)

        testee.selectByUrlOrNewTab("http://www.example.com")

        val value = testee.liveSelectedTab.blockingObserve()?.tabId
        assertEquals("id", value)
        db.close()
    }

    @Test
    fun whenSelectByUrlOrNewTabIfUrlNotExistedInATabThenAddNewTab() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        val testee = tabDataRepository(dao)

        testee.selectByUrlOrNewTab("http://www.example.com")

        val value = testee.liveSelectedTab.blockingObserve()?.url
        assertEquals("http://www.example.com", value)
        db.close()
    }

    @Test
    fun whenAddFromSourceTabEnsureTabEntryContainsExpectedSourceId() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        val sourceTab = TabEntity(tabId = "sourceId", url = "http://www.example.com", position = 0)
        dao.addAndSelectTab(sourceTab)
        val testee = tabDataRepository(dao)

        val addedTabId = testee.addFromSourceTab("http://www.example.com", skipHome = false, sourceTabId = "sourceId")

        val addedTab = testee.liveSelectedTab.blockingObserve()
        assertEquals(addedTabId, addedTab?.tabId)
        assertEquals(addedTab?.sourceTabId, sourceTab.tabId)
    }

    @Test
    fun whenDeleteTabAndSelectSourceLiveSelectedTabReturnsToSourceTab() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        val sourceTab = TabEntity(tabId = "sourceId", url = "http://www.example.com", position = 0)
        val tabToDelete = TabEntity(tabId = "tabToDeleteId", url = "http://www.example.com", position = 1, sourceTabId = "sourceId")
        dao.addAndSelectTab(sourceTab)
        dao.addAndSelectTab(tabToDelete)
        val testee = tabDataRepository(dao)
        var currentSelectedTabId = testee.liveSelectedTab.blockingObserve()?.tabId
        assertEquals(currentSelectedTabId, tabToDelete.tabId)

        testee.deleteTabAndSelectSource("tabToDeleteId")

        currentSelectedTabId = testee.liveSelectedTab.blockingObserve()?.tabId
        assertEquals(currentSelectedTabId, sourceTab.tabId)
    }

    @Test
    fun whenMarkDeletableTrueThenMarksTabAsDeletable() = runTest {
        val testee = tabDataRepository()
        val tab = TabEntity(
            tabId = "tabid",
            position = 0,
            deletable = false,
        )

        testee.markDeletable(tab)

        verify(mockDao).markTabAsDeletable(tab)
    }

    @Test
    fun whenMarkDeletableFalseThenMarksTabAsNonDeletable() = runTest {
        val testee = tabDataRepository()
        val tab = TabEntity(
            tabId = "tabid",
            position = 0,
            deletable = true,
        )

        testee.undoDeletable(tab)

        verify(mockDao).undoDeletableTab(tab)
    }

    @Test
    fun whenPurgeDeletableTabsThenPurgeDeletableTabsAndUpdateSelection() = runTest {
        val testee = tabDataRepository()
        testee.purgeDeletableTabs()

        verify(mockDao).purgeDeletableTabsAndUpdateSelection()
    }

    @Test
    fun whenDaoFLowDeletableTabsEmitsThenDropFirstEmission() = runTest {
        val testee = tabDataRepository()
        val tab = TabEntity("ID", position = 0)

        val job = launch {
            testee.flowDeletableTabs.collect {
                assert(false) { "First value should be skipped" }
            }
        }

        daoDeletableTabs.send(listOf(tab))
        job.cancel()
    }

    @Test
    fun whenDaoFLowDeletableTabsEmitsThenEmit() = runTest {
        val testee = tabDataRepository()
        val tab = TabEntity("ID1", position = 0)
        val expectedTab = TabEntity("ID2", position = 0)

        val job = launch {
            testee.flowDeletableTabs.collect {
                assertEquals(listOf(expectedTab), it)
            }
        }

        daoDeletableTabs.send(listOf(tab)) // dropped
        daoDeletableTabs.send(listOf(expectedTab))
        job.cancel()
    }

    @Test
    fun whenDaoFLowDeletableTabsDoubleEmitsThenDistinctUntilChanged() = runTest {
        val testee = tabDataRepository()
        val tab = TabEntity("ID1", position = 0)

        var count = 0
        val job = launch {
            testee.flowDeletableTabs.collect {
                ++count
                assertEquals(1, count)
            }
        }

        daoDeletableTabs.send(listOf(tab, tab, tab)) // dropped
        daoDeletableTabs.send(listOf(tab, tab, tab))
        daoDeletableTabs.send(listOf(tab, tab, tab))
        daoDeletableTabs.send(listOf(tab, tab, tab))
        daoDeletableTabs.send(listOf(tab, tab, tab))
        job.cancel()
    }

    @Test
    fun whenDeleteTabAndSelectSourceIfTabHadAParentThenEmitParentTabId() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        val sourceTab = TabEntity(tabId = "sourceId", url = "http://www.example.com", position = 0)
        val tabToDelete = TabEntity(tabId = "tabToDeleteId", url = "http://www.example.com", position = 1, sourceTabId = "sourceId")
        dao.addAndSelectTab(sourceTab)
        dao.addAndSelectTab(tabToDelete)
        val testee = tabDataRepository(dao)

        testee.deleteTabAndSelectSource("tabToDeleteId")

        val job = launch {
            testee.childClosedTabs.collect {
                assertEquals("sourceId", it)
            }
        }

        job.cancel()
    }

    private fun tabDataRepository(
        dao: TabsDao = mockDatabase(),
        entityLookup: EntityLookup = mock(),
        allowListRepository: UserAllowListRepository = mock(),
        bypassedSSLCertificatesRepository: BypassedSSLCertificatesRepository = mock(),
        contentBlocking: ContentBlocking = mock(),
        webViewPreviewPersister: WebViewPreviewPersister = mock(),
        faviconManager: FaviconManager = mock(),
    ): TabDataRepository {
        return TabDataRepository(
            dao,
            SiteFactoryImpl(
                entityLookup,
                contentBlocking,
                allowListRepository,
                bypassedSSLCertificatesRepository,
                coroutinesTestRule.testScope,
                coroutinesTestRule.testDispatcherProvider,
            ),
            webViewPreviewPersister,
            faviconManager,
            coroutinesTestRule.testScope,
            coroutinesTestRule.testDispatcherProvider,
        )
    }

    private fun createDatabase(): AppDatabase {
        return Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun mockDatabase(): TabsDao {
        whenever(mockDao.flowDeletableTabs())
            .thenReturn(daoDeletableTabs.consumeAsFlow())

        return mockDao
    }

    companion object {
        const val TAB_ID = "abcd"
    }
}
