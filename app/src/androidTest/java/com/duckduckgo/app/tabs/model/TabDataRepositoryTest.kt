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
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.blockingObserve
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.trackerdetection.EntityLookup
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class TabDataRepositoryTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val mockDao: TabsDao = mock()

    private val mockPrivacyPractices: PrivacyPractices = mock()

    private val mockEntityLookup: EntityLookup = mock()

    private val mockWebViewPreviewPersister: WebViewPreviewPersister = mock()

    private val mockUserEventsStore: UserEventsStore = mock()

    private lateinit var testee: TabDataRepository

    private val mockFaviconManager: FaviconManager = mock()

    private val daoDeletableTabs = Channel<List<TabEntity>>()

    @ExperimentalCoroutinesApi
    @UiThreadTest
    @Before
    fun before() {
        runBlocking {
            whenever(mockDao.flowDeletableTabs())
                .thenReturn(daoDeletableTabs.consumeAsFlow())
            whenever(mockPrivacyPractices.privacyPracticesFor(any())).thenReturn(PrivacyPractices.UNKNOWN)
            testee = tabDataRepository(mockDao)
        }
    }

    @After
    fun after() {
        daoDeletableTabs.close()
    }

    @Test
    fun whenAddNewTabAfterExistingTabWithUrlWithNoHostThenUsesUrlAsTitle() = runTest {
        val badUrl = "//bad/url"
        testee.addNewTabAfterExistingTab(badUrl, "tabid")
        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).insertTabAtPosition(captor.capture())
        assertEquals(badUrl, captor.firstValue.url)
    }

    @Test
    fun whenTabAddDirectlyThenViewedIsTrue() = runTest {
        testee.add("http://www.example.com")

        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).addAndSelectTab(captor.capture())
        assertTrue(captor.firstValue.viewed)
    }

    @Test
    fun whenTabUpdatedAfterOpenInBackgroundThenViewedIsTrue() = runTest {
        testee.addNewTabAfterExistingTab("http://www.example.com", "tabid")
        testee.update("tabid", null)

        val captor = argumentCaptor<Boolean>()
        verify(mockDao).updateUrlAndTitle(any(), anyOrNull(), anyOrNull(), captor.capture())
        assertTrue(captor.firstValue)
    }

    @Test
    fun whenNewTabAddedAfterNonExistingTabThenTitleUrlPositionOfNewTabAreCorrectAndTabIsNotViewed() = runTest {
        testee.addNewTabAfterExistingTab("http://www.example.com", "tabid")

        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).insertTabAtPosition(captor.capture())
        assertNotNull(captor.firstValue.tabId)
        assertEquals(0, captor.firstValue.position)
        assertFalse(captor.firstValue.viewed)
    }

    @Test
    fun whenNewTabAddedAfterExistingTabThenTitleUrlPositionOfNewTabAreCorrect() = runTest {
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
        val createdId = testee.add()
        verify(mockDao).addAndSelectTab(any())
        assertNotNull(testee.retrieveSiteData(createdId))
    }

    @Test
    fun whenAddCalledWithUrlThenTabAddedAndSelectedAndUrlSiteDataCreated() = runTest {
        val url = "http://example.com"
        val createdId = testee.add(url)
        verify(mockDao).addAndSelectTab(any())
        assertNotNull(testee.retrieveSiteData(createdId))
        assertEquals(url, testee.retrieveSiteData(createdId).value!!.url)
    }

    @Test
    fun whenDataDoesNotExistForTabThenRetrieveCreatesIt() {
        assertNotNull(testee.retrieveSiteData(TAB_ID))
    }

    @Test
    fun whenTabDeletedThenTabAndDataCleared() = runTest {
        val addedTabId = testee.add()
        val siteData = testee.retrieveSiteData(addedTabId)

        testee.delete(TabEntity(addedTabId, position = 0))

        verify(mockDao).deleteTabAndUpdateSelection(any())
        assertNotSame(siteData, testee.retrieveSiteData(addedTabId))
    }

    @Test
    fun whenAllDeletedThenTabAndDataCleared() = runTest {
        val addedTabId = testee.add()
        val siteData = testee.retrieveSiteData(addedTabId)

        testee.deleteAll()

        verify(mockDao).deleteAllTabs()
        assertNotSame(siteData, testee.retrieveSiteData(addedTabId))
    }

    @Test
    fun whenIdSelectedThenCurrentUpdated() = runTest {
        testee.select(TAB_ID)
        verify(mockDao).insertTabSelection(eq(TabSelectionEntity(tabId = TAB_ID)))
    }

    @Test
    fun whenAddingFirstTabPositionIsAlwaysZero() = runTest {
        whenever(mockDao.tabs()).thenReturn(emptyList())

        testee.add("http://www.example.com")

        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).addAndSelectTab(captor.capture())
        assertTrue(captor.firstValue.position == 0)
    }

    @Test
    fun whenAddingTabToExistingTabsPositionIsAlwaysIncreased() = runTest {
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
        testee = tabDataRepository(dao)

        testee.add("example.com")

        testee.addDefaultTab()

        assertTrue(testee.liveTabs.blockingObserve()?.size == 1)
    }

    @Test
    fun whenAddDefaultTabToEmptyTabsThenTabIsCreated() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        testee = tabDataRepository(dao)

        testee.addDefaultTab()

        assertTrue(testee.liveTabs.blockingObserve()?.size == 1)
    }

    @Test
    fun whenSelectByUrlOrNewTabIfUrlAlreadyExistedInATabThenSelectTheTab() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        dao.insertTab(TabEntity(tabId = "id", url = "http://www.example.com", skipHome = false, viewed = true, position = 0))
        testee = tabDataRepository(dao)

        testee.selectByUrlOrNewTab("http://www.example.com")

        val value = testee.liveSelectedTab.blockingObserve()?.tabId
        assertEquals("id", value)
        db.close()
    }

    @Test
    fun whenSelectByUrlOrNewTabIfUrlNotExistedInATabThenAddNewTab() = runTest {
        val db = createDatabase()
        val dao = db.tabsDao()
        testee = tabDataRepository(dao)

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
        testee = tabDataRepository(dao)

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
        testee = tabDataRepository(dao)
        var currentSelectedTabId = testee.liveSelectedTab.blockingObserve()?.tabId
        assertEquals(currentSelectedTabId, tabToDelete.tabId)

        testee.deleteTabAndSelectSource("tabToDeleteId")

        currentSelectedTabId = testee.liveSelectedTab.blockingObserve()?.tabId
        assertEquals(currentSelectedTabId, sourceTab.tabId)
    }

    @Test
    fun whenMarkDeletableTrueThenMarksTabAsDeletable() = runTest {
        val tab = TabEntity(
            tabId = "tabid",
            position = 0,
            deletable = false
        )

        testee.markDeletable(tab)

        verify(mockDao).markTabAsDeletable(tab)
    }

    @Test
    fun whenMarkDeletableFalseThenMarksTabAsNonDeletable() = runTest {
        val tab = TabEntity(
            tabId = "tabid",
            position = 0,
            deletable = true
        )

        testee.undoDeletable(tab)

        verify(mockDao).undoDeletableTab(tab)
    }

    @Test
    fun whenPurgeDeletableTabsThenPurgeDeletableTabsAndUpdateSelection() = runTest {
        testee.purgeDeletableTabs()

        verify(mockDao).purgeDeletableTabsAndUpdateSelection()
    }

    @Test
    @ExperimentalCoroutinesApi
    fun whenDaoFLowDeletableTabsEmitsThenDropFirstEmission() = runTest {
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
    @ExperimentalCoroutinesApi
    fun whenDaoFLowDeletableTabsEmitsThenEmit() = runTest {
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
    @ExperimentalCoroutinesApi
    fun whenDaoFLowDeletableTabsDoubleEmitsThenDistinctUntilChanged() = runTest {
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
        testee = tabDataRepository(dao)

        testee.deleteTabAndSelectSource("tabToDeleteId")

        val job = launch {
            testee.childClosedTabs.collect {
                assertEquals("sourceId", it)
            }
        }

        job.cancel()
    }

    private fun tabDataRepository(dao: TabsDao): TabDataRepository {
        return TabDataRepository(
            dao,
            SiteFactory(mockPrivacyPractices, mockEntityLookup),
            mockWebViewPreviewPersister,
            mockFaviconManager,
            TestScope()
        )
    }

    private fun createDatabase(): AppDatabase {
        return Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    companion object {
        const val TAB_ID = "abcd"
    }
}
