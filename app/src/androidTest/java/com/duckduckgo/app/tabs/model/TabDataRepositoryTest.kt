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
import androidx.lifecycle.MutableLiveData
import androidx.room.Room
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.blockingObserve
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.global.useourapp.UseOurAppDetector
import com.duckduckgo.app.global.useourapp.UseOurAppDetector.Companion.USE_OUR_APP_DOMAIN
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.trackerdetection.EntityLookup
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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

    private val useOurAppDetector = UseOurAppDetector(mockUserEventsStore)

    private lateinit var testee: TabDataRepository

    @UiThreadTest
    @Before
    fun before() {
        runBlocking {
            whenever(mockPrivacyPractices.privacyPracticesFor(any())).thenReturn(PrivacyPractices.UNKNOWN)
            testee = TabDataRepository(
                mockDao,
                SiteFactory(mockPrivacyPractices, mockEntityLookup),
                mockWebViewPreviewPersister,
                useOurAppDetector
            )
        }
    }

    @Test
    fun whenAddNewTabAfterExistingTabWithUrlWithNoHostThenUsesUrlAsTitle() = runBlocking<Unit> {

        val badUrl = "//bad/url"
        testee.addNewTabAfterExistingTab(badUrl, "tabid")
        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).insertTabAtPosition(captor.capture())
        assertEquals(badUrl, captor.firstValue.url)
    }

    @Test
    fun whenTabAddDirectlyThenViewedIsTrue() = runBlocking<Unit> {
        testee.add("http://www.example.com")

        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).addAndSelectTab(captor.capture())
        assertTrue(captor.firstValue.viewed)
    }

    @Test
    fun whenTabUpdatedAfterOpenInBackgroundThenViewedIsTrue() = runBlocking<Unit> {
        testee.addNewTabAfterExistingTab("http://www.example.com", "tabid")
        testee.update("tabid", null)

        val captor = argumentCaptor<Boolean>()
        verify(mockDao).updateUrlAndTitle(any(), anyOrNull(), anyOrNull(), captor.capture())
        assertTrue(captor.firstValue)
    }

    @Test
    fun whenNewTabAddedAfterNonExistingTabThenTitleUrlPositionOfNewTabAreCorrectAndTabIsNotViewed() = runBlocking<Unit> {
        testee.addNewTabAfterExistingTab("http://www.example.com", "tabid")

        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).insertTabAtPosition(captor.capture())
        assertNotNull(captor.firstValue.tabId)
        assertEquals(0, captor.firstValue.position)
        assertFalse(captor.firstValue.viewed)
    }

    @Test
    fun whenNewTabAddedAfterExistingTabThenTitleUrlPositionOfNewTabAreCorrect() = runBlocking<Unit> {
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
    fun whenAddCalledThenTabAddedAndSelectedAndBlankSiteDataCreated() = runBlocking<Unit> {
        val createdId = testee.add()
        verify(mockDao).addAndSelectTab(any())
        assertNotNull(testee.retrieveSiteData(createdId))
    }

    @Test
    fun whenAddCalledWithUrlThenTabAddedAndSelectedAndUrlSiteDataCreated() = runBlocking<Unit> {
        val url = "http://example.com"
        val createdId = testee.add(url)
        verify(mockDao).addAndSelectTab(any())
        assertNotNull(testee.retrieveSiteData(createdId))
        assertEquals(url, testee.retrieveSiteData(createdId).value!!.url)
    }

    @Test
    fun whenAddRecordCalledThenTabAddedAndSiteDataAdded() = runBlocking<Unit> {
        val record = MutableLiveData<Site>()
        testee.add(TAB_ID, record)
        verify(mockDao).addAndSelectTab(any())
        assertSame(record, testee.retrieveSiteData(TAB_ID))
    }

    @Test
    fun whenDataExistsForTabThenRetrieveReturnsIt() = runBlocking<Unit> {
        val record = MutableLiveData<Site>()
        testee.add(TAB_ID, record)
        assertSame(record, testee.retrieveSiteData(TAB_ID))
    }

    @Test
    fun whenDataDoesNotExistForTabThenRetrieveCreatesIt() {
        assertNotNull(testee.retrieveSiteData(TAB_ID))
    }

    @Test
    fun whenTabDeletedThenTabAndDataCleared() = runBlocking<Unit> {
        val siteData = MutableLiveData<Site>()
        testee.add(TAB_ID, siteData)

        testee.delete(TabEntity(TAB_ID, position = 0))
        verify(mockDao).deleteTabAndUpdateSelection(any())
        assertNotSame(siteData, testee.retrieveSiteData(TAB_ID))
    }

    @Test
    fun whenAllDeletedThenTabAndDataCleared() = runBlocking<Unit> {
        val siteData = MutableLiveData<Site>()
        testee.add(TAB_ID, siteData)
        testee.deleteAll()
        verify(mockDao).deleteAllTabs()
        assertNotSame(siteData, testee.retrieveSiteData(TAB_ID))
    }

    @Test
    fun whenIdSelectedThenCurrentUpdated() = runBlocking<Unit> {
        testee.select(TAB_ID)
        verify(mockDao).insertTabSelection(eq(TabSelectionEntity(tabId = TAB_ID)))
    }

    @Test
    fun whenAddingFirstTabPositionIsAlwaysZero() = runBlocking<Unit> {
        whenever(mockDao.tabs()).thenReturn(emptyList())

        testee.add("http://www.example.com")

        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).addAndSelectTab(captor.capture())

        assertTrue(captor.firstValue.position == 0)
    }

    @Test
    fun whenAddingTabToExistingTabsPositionIsAlwaysIncreased() = runBlocking<Unit> {
        val tab0 = TabEntity("tabid", position = 0)
        val existingTabs = listOf(tab0)

        whenever(mockDao.tabs()).thenReturn(existingTabs)

        testee.add("http://www.example.com")

        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).addAndSelectTab(captor.capture())

        assertTrue(captor.firstValue.position == 1)
    }

    @Test
    fun whenSelectByUrlOrNewTabIfUrlAlreadyExistedInATabThenSelectTheTab() = runBlocking<Unit> {
        val db = createDatabase()
        val dao = db.tabsDao()
        dao.insertTab(TabEntity(tabId = "id", url = "http://www.example.com", skipHome = false, viewed = true, position = 0))

        testee = TabDataRepository(dao, SiteFactory(mockPrivacyPractices, mockEntityLookup), mockWebViewPreviewPersister, useOurAppDetector)

        testee.selectByUrlOrNewTab("http://www.example.com")

        val value = testee.liveSelectedTab.blockingObserve()?.tabId
        assertEquals("id", value)

        db.close()
    }

    @Test
    fun whenSelectByUrlOrNewTabIfUrlNotExistedInATabThenAddNewTab() = runBlocking<Unit> {
        val db = createDatabase()
        val dao = db.tabsDao()

        testee = TabDataRepository(dao, SiteFactory(mockPrivacyPractices, mockEntityLookup), mockWebViewPreviewPersister, useOurAppDetector)

        testee.selectByUrlOrNewTab("http://www.example.com")

        val value = testee.liveSelectedTab.blockingObserve()?.url
        assertEquals("http://www.example.com", value)

        db.close()
    }

    @Test
    fun whenSelectByUrlOrNewTabIfUrlAlreadyExistedInATabAndMatchesTheUseOurAppDomainThenSelectTheTab() = runBlocking<Unit> {
        val db = createDatabase()
        val dao = db.tabsDao()
        dao.insertTab(TabEntity(tabId = "id", url = "http://www.$USE_OUR_APP_DOMAIN/test", skipHome = false, viewed = true, position = 0))

        testee = TabDataRepository(dao, SiteFactory(mockPrivacyPractices, mockEntityLookup), mockWebViewPreviewPersister, useOurAppDetector)

        testee.selectByUrlOrNewTab("http://m.$USE_OUR_APP_DOMAIN")

        val value = testee.liveSelectedTab.blockingObserve()?.tabId
        assertEquals("id", value)

        db.close()
    }

    @Test
    fun whenSelectByUrlOrNewTabIfUrlNotExistedInATabAndUrlMatchesUseOurAppDomainThenAddNewTabWithCorrectUrl() = runBlocking<Unit> {
        val db = createDatabase()
        val dao = db.tabsDao()

        testee = TabDataRepository(dao, SiteFactory(mockPrivacyPractices, mockEntityLookup), mockWebViewPreviewPersister, useOurAppDetector)

        testee.selectByUrlOrNewTab("http://m.$USE_OUR_APP_DOMAIN")

        val value = testee.liveSelectedTab.blockingObserve()?.url
        assertEquals("http://m.$USE_OUR_APP_DOMAIN", value)

        db.close()
    }

    @Test
    fun whenAddWithSourceEnsureTabEntryContainsExpectedSourceId() = runBlocking<Unit> {
        val db = createDatabase()
        val dao = db.tabsDao()
        val sourceTab = TabEntity(tabId = "sourceId", url = "http://www.example.com", position = 0)
        dao.addAndSelectTab(sourceTab)

        testee = TabDataRepository(dao, SiteFactory(mockPrivacyPractices, mockEntityLookup), mockWebViewPreviewPersister, useOurAppDetector)

        val addedTabId = testee.addFromCurrentTab("http://www.example.com", skipHome = false, isDefaultTab = false)
        val addedTab = testee.liveSelectedTab.blockingObserve()
        assertEquals(addedTabId, addedTab?.tabId)
        assertEquals(addedTab?.sourceTabId, sourceTab.tabId)
    }

    @Test
    fun whenDeleteCurrentTabAndSelectSourceLiveSelectedTabReturnsToSourceTab() = runBlocking<Unit> {
        val db = createDatabase()
        val dao = db.tabsDao()
        val sourceTab = TabEntity(tabId = "sourceId", url = "http://www.example.com", position = 0)
        val tabToDelete = TabEntity(tabId = "tabToDeleteId", url = "http://www.example.com", position = 1, sourceTabId = "sourceId")
        dao.addAndSelectTab(sourceTab)
        dao.addAndSelectTab(tabToDelete)

        testee = TabDataRepository(dao, SiteFactory(mockPrivacyPractices, mockEntityLookup), mockWebViewPreviewPersister, useOurAppDetector)

        var currentSelectedTabId = testee.liveSelectedTab.blockingObserve()?.tabId
        assertEquals(currentSelectedTabId, tabToDelete.tabId)
        testee.deleteCurrentTabAndSelectSource()
        currentSelectedTabId = testee.liveSelectedTab.blockingObserve()?.tabId
        assertEquals(currentSelectedTabId, sourceTab.tabId)
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
