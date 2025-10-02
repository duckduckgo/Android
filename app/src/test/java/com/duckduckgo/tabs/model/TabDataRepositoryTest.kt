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

package com.duckduckgo.tabs.model

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.certificates.BypassedSSLCertificatesRepository
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.model.SiteFactoryImpl
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.tabs.model.TabDataRepository
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSelectionEntity
import com.duckduckgo.app.tabs.store.TabSwitcherDataStore
import com.duckduckgo.app.trackerdetection.EntityLookup
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.common.test.blockingObserve
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.privacy.config.api.ContentBlocking
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
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

    private val mockDuckPlayer: DuckPlayer = mock()

    private val daoDeletableTabs = Channel<List<TabEntity>>()

    private val tabManagerFeatureFlags = FakeFeatureToggleFactory.create(TabManagerFeatureFlags::class.java)

    private val mockWebViewSessionStorage: WebViewSessionStorage = mock()

    private val mockAdClickManager: AdClickManager = mock()

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
        verify(mockDao).addAndSelectTab(captor.capture(), any())
        assertTrue(captor.firstValue.viewed)
    }

    @Test
    fun whenTabAddAndTabInsertionFixesOnThenAddAndSelectIsCalledWithUpdateIfBlankParent() = runTest {
        val testee = tabDataRepository()
        tabManagerFeatureFlags.tabInsertionFixes().setRawStoredState(State(enable = true))
        testee.add("http://www.example.com")

        verify(mockDao).addAndSelectTab(any(), eq(true))
    }

    @Test
    fun whenTabAddAndTabInsertionFixesOffThenAddAndSelectIsCalledWithUpdateIfBlankParentFalse() = runTest {
        val testee = tabDataRepository()
        tabManagerFeatureFlags.tabInsertionFixes().setRawStoredState(State(enable = false))
        testee.add("http://www.example.com")

        verify(mockDao).addAndSelectTab(any(), eq(false))
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
        verify(mockDao).addAndSelectTab(any(), any())
        assertNotNull(testee.retrieveSiteData(createdId))
    }

    @Test
    fun whenAddCalledWithUrlThenTabAddedAndSelectedAndUrlSiteDataCreated() = runTest {
        val testee = tabDataRepository()
        val url = "http://example.com"
        val createdId = testee.add(url)
        verify(mockDao).addAndSelectTab(any(), any())
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
        verify(mockDao).addAndSelectTab(captor.capture(), any())
        assertTrue(captor.firstValue.position == 0)
    }

    @Test
    fun whenAddingTabToExistingTabsPositionIsAlwaysIncreased() = runTest {
        val testee = tabDataRepository()
        val tab0 = TabEntity("tabid", position = 0)
        val existingTabs = listOf(tab0)

        whenever(mockDao.tabs()).thenReturn(existingTabs)
        whenever(mockDao.lastTab()).thenReturn(existingTabs.last())

        testee.add("http://www.example.com")

        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).addAndSelectTab(captor.capture(), any())
        assertTrue(captor.firstValue.position == 1)
    }

    @Test
    fun whenAddDefaultTabToExistingListOfTabsThenTabIsNotCreated() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        val testee = tabDataRepository(dao)

        testee.add("example.com")

        testee.addDefaultTab()

        assertTrue(testee.liveTabs.blockingObserve()?.size == 1)
    }

    @Test
    fun whenAddDefaultTabToEmptyTabsThenTabIsCreated() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        val testee = tabDataRepository(dao)

        testee.addDefaultTab()

        assertTrue(testee.liveTabs.blockingObserve()?.size == 1)
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
    fun whenGetDeletableTabIdsCalledThenReturnsAListWithDeletableTabIds() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        dao.insertTab(TabEntity(tabId = "id_1", url = "http://www.example.com", skipHome = false, viewed = true, position = 0, deletable = true))
        dao.insertTab(TabEntity(tabId = "id_2", url = "http://www.example.com", skipHome = false, viewed = true, position = 1, deletable = false))
        dao.insertTab(TabEntity(tabId = "id_3", url = "http://www.example.com", skipHome = false, viewed = true, position = 2, deletable = true))
        val testee = tabDataRepository(dao)

        val deletableTabIds = testee.getDeletableTabIds()

        assertEquals(listOf("id_1", "id_3"), deletableTabIds)
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

    @Test
    fun getOpenTabCountReturnsCorrectCount() = runTest {
        // Arrange: Add some tabs to the repository
        whenever(mockDao.tabs()).thenReturn(
            listOf(
                TabEntity(tabId = "tab1"),
                TabEntity(tabId = "tab2"),
                TabEntity(tabId = "tab3"),
            ),
        )
        val testee = tabDataRepository()

        val openTabCount = testee.getOpenTabCount()

        // Assert: Verify the count is correct
        assertEquals(3, openTabCount)
    }

    @Test
    fun getActiveTabCountReturnsZeroWhenNoTabs() = runTest {
        // Arrange: No tabs in the repository
        whenever(mockDao.tabs()).thenReturn(emptyList())
        val testee = tabDataRepository()

        val inactiveTabCount = testee.countTabsAccessedWithinRange(0, 7)

        // Assert: Verify the count is zero
        assertEquals(0, inactiveTabCount)
    }

    @Test
    fun getActiveTabCountReturnsZeroWhenNullTabs() = runTest {
        // Arrange: Only null tabs in the repository
        val tab1 = TabEntity(tabId = "tab1")
        whenever(mockDao.tabs()).thenReturn(listOf(tab1))
        val testee = tabDataRepository()

        val inactiveTabCount = testee.countTabsAccessedWithinRange(0, 7)

        // Assert: Verify the count is zero
        assertEquals(0, inactiveTabCount)
    }

    @Test
    fun getActiveTabCountReturnsCorrectCountWhenTabsYoungerThanSpecifiedDay() = runTest {
        // Arrange: No tabs in the repository
        val now = now()
        val tab1 = TabEntity(tabId = "tab1", lastAccessTime = now.minusDays(6))
        val tab2 = TabEntity(tabId = "tab2", lastAccessTime = now.minusDays(8))
        val tab3 = TabEntity(tabId = "tab3", lastAccessTime = now.minusDays(10))
        val tab4 = TabEntity(tabId = "tab4")
        whenever(mockDao.tabs()).thenReturn(listOf(tab1, tab2, tab3, tab4))
        val testee = tabDataRepository()

        val inactiveTabCount = testee.countTabsAccessedWithinRange(0, 9)

        // Assert: Verify the count is 2
        assertEquals(2, inactiveTabCount)
    }

    @Test
    fun getInactiveTabCountReturnsZeroWhenNoTabs() = runTest {
        // Arrange: No tabs in the repository
        whenever(mockDao.tabs()).thenReturn(emptyList())
        val testee = tabDataRepository()

        val inactiveTabCount = testee.countTabsAccessedWithinRange(7, 12)

        // Assert: Verify the count is zero
        assertEquals(0, inactiveTabCount)
    }

    @Test
    fun getInactiveTabCountReturnsCorrectCountWhenAllTabsOlderThanSpecifiedDay() = runTest {
        // Arrange: Add some tabs with different last access times
        val now = now()
        val tab1 = TabEntity(tabId = "tab1", lastAccessTime = now.minusDays(8))
        val tab2 = TabEntity(tabId = "tab2", lastAccessTime = now.minusDays(10))
        val tab3 = TabEntity(tabId = "tab3", lastAccessTime = now.minusDays(9).minusSeconds(1))
        val tab4 = TabEntity(tabId = "tab4")
        whenever(mockDao.tabs()).thenReturn(listOf(tab1, tab2, tab3, tab4))
        val testee = tabDataRepository()

        val inactiveTabCount = testee.countTabsAccessedWithinRange(9)

        // Assert: Verify the count is correct
        assertEquals(2, inactiveTabCount)
    }

    @Test
    fun getInactiveTabCountReturnsCorrectCountWhenAllTabsInactiveWithinRange() = runTest {
        // Arrange: Add some tabs with different last access times
        val now = now()
        val tab1 = TabEntity(tabId = "tab1", lastAccessTime = now.minusDays(8))
        val tab2 = TabEntity(tabId = "tab2", lastAccessTime = now.minusDays(10))
        val tab3 = TabEntity(tabId = "tab3", lastAccessTime = now.minusDays(9))
        val tab4 = TabEntity(tabId = "tab4")
        whenever(mockDao.tabs()).thenReturn(listOf(tab1, tab2, tab3, tab4))
        val testee = tabDataRepository()

        val inactiveTabCount = testee.countTabsAccessedWithinRange(7, 12)

        // Assert: Verify the count is correct
        assertEquals(3, inactiveTabCount)
    }

    @Test
    fun getInactiveTabCountReturnsZeroWhenNoTabsInactiveWithinRange() = runTest {
        // Arrange: Add some tabs with different last access times
        val now = now()
        val tab1 = TabEntity(tabId = "tab1", lastAccessTime = now.minusDays(5))
        val tab2 = TabEntity(tabId = "tab2", lastAccessTime = now.minusDays(6))
        val tab3 = TabEntity(tabId = "tab3", lastAccessTime = now.minusDays(13))
        val tab4 = TabEntity(tabId = "tab4")
        whenever(mockDao.tabs()).thenReturn(listOf(tab1, tab2, tab3, tab4))
        val testee = tabDataRepository()

        val inactiveTabCount = testee.countTabsAccessedWithinRange(7, 12)

        // Assert: Verify the count is zero
        assertEquals(0, inactiveTabCount)
    }

    @Test
    fun getInactiveTabCountReturnsCorrectCountWhenSomeTabsInactiveWithinRange() = runTest {
        // Arrange: Add some tabs with different last access times
        val now = now()
        val tab1 = TabEntity(tabId = "tab1", lastAccessTime = now.minusDays(5))
        val tab2 = TabEntity(tabId = "tab2", lastAccessTime = now.minusDays(10))
        val tab3 = TabEntity(tabId = "tab3", lastAccessTime = now.minusDays(15))
        val tab4 = TabEntity(tabId = "tab4")
        whenever(mockDao.tabs()).thenReturn(listOf(tab1, tab2, tab3, tab4))
        val testee = tabDataRepository()

        val inactiveTabCount = testee.countTabsAccessedWithinRange(7, 12)

        // Assert: Verify the count is correct
        assertEquals(1, inactiveTabCount)
    }

    @Test
    fun whenDeleteTabsThenTabsDeletedAndDataCleared() = runTest {
        val testee = tabDataRepository()
        val tabIds = listOf("tabid1", "tabid2")

        testee.deleteTabs(tabIds)

        verify(mockDao).deleteTabsAndUpdateSelection(tabIds)
        tabIds.forEach { tabId ->
            assertNull(testee.retrieveSiteData(tabId).value)
            verify(mockWebViewSessionStorage).deleteSession(tabId)
            verify(mockAdClickManager).clearTabId(tabId)
        }
    }

    @Test
    fun whenPurgeDeletableTabsThenPurgeDeletableTabsAndClearData() = runTest {
        val testee = tabDataRepository()
        val tabIds = listOf("tabid1", "tabid2")
        whenever(mockDao.getDeletableTabIds()).thenReturn(tabIds)

        testee.purgeDeletableTabs()

        verify(mockDao).purgeDeletableTabsAndUpdateSelection()
        tabIds.forEach { tabId ->
            assertNull(testee.retrieveSiteData(tabId).value)
            verify(mockWebViewSessionStorage).deleteSession(tabId)
            verify(mockAdClickManager).clearTabId(tabId)
        }
    }

    @Test
    fun whenFlowTabsCollectedThenEmitsTabs() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        val tab1 = TabEntity(tabId = "tab1", url = "http://example1.com", position = 0)
        val tab2 = TabEntity(tabId = "tab2", url = "http://example2.com", position = 1)
        dao.insertTab(tab1)
        dao.insertTab(tab2)
        val testee = tabDataRepository(dao)

        val tabs = testee.flowTabs.first()

        assertEquals(2, tabs.size)
        assertEquals(tab1.tabId, tabs[0].tabId)
        assertEquals(tab2.tabId, tabs[1].tabId)
        db.close()
    }

    @Test
    fun whenFlowSelectedTabCollectedThenEmitsSelectedTab() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        val tab1 = TabEntity(tabId = "tab1", url = "http://example1.com", position = 0)
        val tab2 = TabEntity(tabId = "tab2", url = "http://example2.com", position = 1)
        dao.addAndSelectTab(tab1)
        dao.addAndSelectTab(tab2)
        val testee = tabDataRepository(dao)

        val selectedTab = testee.flowSelectedTab.first()

        assertNotNull(selectedTab)
        assertEquals(tab2.tabId, selectedTab?.tabId)
        db.close()
    }

    @Test
    fun whenFlowSelectedTabCollectedWithNoSelectionThenEmitsNull() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        val testee = tabDataRepository(dao)

        val selectedTab = testee.flowSelectedTab.first()

        assertNull(selectedTab)
        db.close()
    }

    @Test
    fun whenTabsChangeFlowTabsEmitsUpdatedList() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        val testee = tabDataRepository(dao)

        // Initial state: empty
        val initialTabs = testee.flowTabs.first()
        assertEquals(0, initialTabs.size)

        // Add first tab
        val tab1 = TabEntity(tabId = "tab1", url = "http://example1.com", position = 0)
        dao.insertTab(tab1)
        val tabsAfterFirst = testee.flowTabs.first()
        assertEquals(1, tabsAfterFirst.size)
        assertEquals(tab1.tabId, tabsAfterFirst[0].tabId)

        // Add second tab
        val tab2 = TabEntity(tabId = "tab2", url = "http://example2.com", position = 1)
        dao.insertTab(tab2)
        val tabsAfterSecond = testee.flowTabs.first()
        assertEquals(2, tabsAfterSecond.size)
        assertEquals(tab1.tabId, tabsAfterSecond[0].tabId)
        assertEquals(tab2.tabId, tabsAfterSecond[1].tabId)

        db.close()
    }

    private fun tabDataRepository(
        dao: TabsDao = mockDatabase(),
        entityLookup: EntityLookup = mock(),
        allowListRepository: UserAllowListRepository = mock(),
        bypassedSSLCertificatesRepository: BypassedSSLCertificatesRepository = mock(),
        contentBlocking: ContentBlocking = mock(),
        webViewPreviewPersister: WebViewPreviewPersister = mock(),
        faviconManager: FaviconManager = mock(),
        tabSwitcherDataStore: TabSwitcherDataStore = mock(),
        duckDuckGoUrlDetector: DuckDuckGoUrlDetector = mock(),
        timeProvider: CurrentTimeProvider = FakeTimeProvider(),
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
                duckDuckGoUrlDetector,
                mockDuckPlayer,
            ),
            webViewPreviewPersister,
            faviconManager,
            tabSwitcherDataStore,
            timeProvider,
            coroutinesTestRule.testScope,
            coroutinesTestRule.testDispatcherProvider,
            mockAdClickManager,
            mockWebViewSessionStorage,
            tabManagerFeatureFlags,
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
        whenever(mockDao.liveTabs())
            .thenReturn(MutableLiveData())
        whenever(mockDao.liveSelectedTab())
            .thenReturn(MutableLiveData())

        return mockDao
    }

    private fun now(): LocalDateTime {
        return FakeTimeProvider().localDateTimeNow()
    }

    companion object {
        const val TAB_ID = "abcd"
    }

    private class FakeTimeProvider : CurrentTimeProvider {
        var currentTime: Instant = Instant.parse("2024-10-16T00:00:00.00Z")

        override fun currentTimeMillis(): Long = currentTime.toEpochMilli()
        override fun elapsedRealtime(): Long = throw UnsupportedOperationException()
        override fun localDateTimeNow(): LocalDateTime = currentTime.atZone(ZoneOffset.UTC).toLocalDateTime()
    }
}
