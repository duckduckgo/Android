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

package com.duckduckgo.app.tabs.model

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.MutableLiveData
import android.support.test.annotation.UiThreadTest
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.privacy.store.TermsOfServiceStore
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.nhaarman.mockito_kotlin.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class TabDataRepositoryTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @Mock
    private lateinit var mockDao: TabsDao

    @Mock
    private lateinit var mockTermsOfServiceStore: TermsOfServiceStore

    private lateinit var testee: TabDataRepository

    @UiThreadTest
    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        testee = TabDataRepository(mockDao, SiteFactory(mockTermsOfServiceStore, TrackerNetworks()))
    }

    @Test
    fun whenNewTabAddedAfterNonExistingTabThenTitleUrlPositionOfNewTabAreCorrect() {
        testee.addNewTabAfterExistingTab("http://www.example.com", "tabid")

        val captor = argumentCaptor<TabEntity>()
        verify(mockDao).insertTabAtPosition(captor.capture())
        assertNotNull(captor.firstValue.tabId)
        assertEquals(0, captor.firstValue.position)
    }

    @Test
    fun whenNewTabAddedAfterExistingTabThenTitleUrlPositionOfNewTabAreCorrect() {
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
    fun whenAddCalledThenTabAddedAndSelectedAndBlankSiteDataCreated() {
        val createdId = testee.add()
        verify(mockDao).addAndSelectTab(any())
        assertNotNull(testee.retrieveSiteData(createdId))
    }

    @Test
    fun whenAddCalledWithUrlThenTabAddedAndSelectedAndUrlSiteDataCreated() {
        val url = "http://example.com"
        val createdId = testee.add(url)
        verify(mockDao).addAndSelectTab(any())
        assertNotNull(testee.retrieveSiteData(createdId))
        assertEquals(url, testee.retrieveSiteData(createdId).value!!.url)
    }

    @Test
    fun whenAddRecordCalledThenTabAddedAndSiteDataAdded() {
        val record = MutableLiveData<Site>()
        testee.add(TAB_ID, record)
        verify(mockDao).addAndSelectTab(any())
        assertSame(record, testee.retrieveSiteData(TAB_ID))
    }

    @Test
    fun whenDataExistsForTabThenRetrieveReturnsIt() {
        val record = MutableLiveData<Site>()
        testee.add(TAB_ID, record)
        assertSame(record, testee.retrieveSiteData(TAB_ID))
    }

    @Test
    fun whenDataDoesNotExistForTabThenRetrieveCreatesIt() {
        assertNotNull(testee.retrieveSiteData(TAB_ID))
    }

    @Test
    fun whenTabDeletedThenTabAndDataCleared() {
        val siteData = MutableLiveData<Site>()
        testee.add(TAB_ID, siteData)

        testee.delete(TabEntity(TAB_ID, position = 0))
        verify(mockDao).deleteTabAndUpdateSelection(any())
        assertNotSame(siteData, testee.retrieveSiteData(TAB_ID))
    }

    @Test
    fun whenAllDeletedThenTabAndDataCleared() {
        val siteData = MutableLiveData<Site>()
        testee.add(TAB_ID, siteData)
        testee.deleteAll()
        verify(mockDao).deleteAllTabs()
        assertNotSame(siteData, testee.retrieveSiteData(TAB_ID))
    }

    @Test
    fun whenIdSelectedThenCurrentUpdated() {
        testee.select(TAB_ID)
        verify(mockDao).insertTabSelection(eq(TabSelectionEntity(tabId = TAB_ID)))
    }

    companion object {
        const val TAB_ID = "abcd"
    }

}