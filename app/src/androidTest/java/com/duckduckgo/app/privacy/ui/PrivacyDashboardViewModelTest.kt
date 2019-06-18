/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.privacy.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.NetworkLeaderboardEntry
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.GOOD
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.UNKNOWN
import com.duckduckgo.app.privacy.store.PrivacySettingsStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.PRIVACY_DASHBOARD_OPENED
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PrivacyDashboardViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private var viewStateObserver: Observer<PrivacyDashboardViewModel.ViewState> = mock()
    private var settingStore: PrivacySettingsStore = mock()
    private var networkLeaderboardDao: NetworkLeaderboardDao = mock()
    private var networkLeaderboardLiveData: LiveData<List<NetworkLeaderboardEntry>> = mock()
    private var sitesVisitedLiveData: LiveData<Int> = mock()
    private var mockPixel: Pixel = mock()

    private val testee: PrivacyDashboardViewModel by lazy {
        val model = PrivacyDashboardViewModel(settingStore, networkLeaderboardDao, mockPixel)
        model.viewState.observeForever(viewStateObserver)
        model
    }

    @Before
    fun before() {
        whenever(sitesVisitedLiveData.value).thenReturn(0)
        whenever(networkLeaderboardLiveData.value).thenReturn(emptyList())
        whenever(networkLeaderboardDao.sitesVisited()).thenReturn(sitesVisitedLiveData)
        whenever(networkLeaderboardDao.trackerNetworkLeaderboard()).thenReturn(networkLeaderboardLiveData)
    }

    @After
    fun after() {
        testee.viewState.removeObserver(viewStateObserver)
        testee.onCleared()
    }

    @Test
    fun whenViewModelInitialisedThenPixelIsFired() {
        testee // init
        verify(mockPixel).fire(PRIVACY_DASHBOARD_OPENED)
    }

    @Test
    fun whenNoDataThenDefaultValuesAreUsed() {
        val viewState = testee.viewState.value!!
        assertEquals("", viewState.domain)
        assertEquals(PrivacyGrade.UNKNOWN, viewState.beforeGrade)
        assertEquals(PrivacyGrade.UNKNOWN, viewState.afterGrade)
        assertEquals(HttpsStatus.SECURE, viewState.httpsStatus)
        assertTrue(viewState.allTrackersBlocked)
        assertEquals(UNKNOWN, testee.viewState.value!!.practices)
    }

    @Test
    fun whenPrivacyInitiallyOnAndSwitchedOffThenShouldReloadIsTrue() {
        whenever(settingStore.privacyOn)
            .thenReturn(true)
            .thenReturn(false)
        assertTrue(testee.viewState.value!!.shouldReloadPage)
    }

    @Test
    fun whenPrivacyInitiallyOnAndUnchangedThenShouldReloadIsFalse() {
        whenever(settingStore.privacyOn).thenReturn(true)
        assertFalse(testee.viewState.value!!.shouldReloadPage)
    }

    @Test
    fun whenPrivacyInitiallyOffAndSwitchedOnThenShouldReloadIsTrue() {
        whenever(settingStore.privacyOn)
            .thenReturn(false)
            .thenReturn(true)
        assertTrue(testee.viewState.value!!.shouldReloadPage)
    }

    @Test
    fun whenPrivacyInitiallyOffAndUnchangedThenShouldReloadIsFalse() {
        whenever(settingStore.privacyOn).thenReturn(false)
        assertFalse(testee.viewState.value!!.shouldReloadPage)
    }

    @Test
    fun whenSiteGradesAreUpdatedThenViewModelGradesAreUpdated() {
        val site = site(grade = PrivacyGrade.D, improvedGrade = PrivacyGrade.B)
        testee.onSiteChanged(site)
        assertEquals(PrivacyGrade.D, testee.viewState.value?.beforeGrade)
        assertEquals(PrivacyGrade.B, testee.viewState.value?.afterGrade)
    }

    @Test
    fun whenSiteHttpsStatusIsUpdatedThenViewModelIsUpdated() {
        testee.onSiteChanged(site(https = HttpsStatus.MIXED))
        assertEquals(HttpsStatus.MIXED, testee.viewState.value?.httpsStatus)
    }

    @Test
    fun whenAllBlockedUpdatedToFalseThenViewModelIsUpdated() {
        testee.onSiteChanged(site(allTrackersBlocked = false))
        assertEquals(false, testee.viewState.value!!.allTrackersBlocked)
    }

    @Test
    fun whenAllBlockedUpdatedToTrueThenViewModelIsUpdated() {
        testee.onSiteChanged(site(allTrackersBlocked = true))
        assertEquals(true, testee.viewState.value!!.allTrackersBlocked)
    }

    @Test
    fun whenPrivacyPracticesAreUpdatedThenPracticesAreUpdatedInViewModel() {
        val practices = PrivacyPractices.Practices(0, GOOD, emptyList(), emptyList())
        testee.onSiteChanged(site(privacyPractices = practices))
        assertEquals(GOOD, testee.viewState.value!!.practices)
    }

    @Test
    fun whenNetworkCountIsThreeAndTotalSitesIsThirtyOneThenShowSummaryIsTrue() {
        val first = NetworkLeaderboardEntry("Network1", 5)
        val second = NetworkLeaderboardEntry("Network2", 3)
        val third = NetworkLeaderboardEntry("Network3", 3)
        testee.onTrackerNetworkEntriesChanged(listOf(first, second, third))
        testee.onSitesVisitedChanged(31)
        assertTrue(testee.viewState.value!!.shouldShowTrackerNetworkLeaderboard)
    }

    @Test
    fun whenNetworkCountIsTwoAndTotalSitesIsThirtyOneThenShowSummaryIsFalse() {
        val first = NetworkLeaderboardEntry("Network1", 5)
        val second = NetworkLeaderboardEntry("Network2", 3)
        testee.onTrackerNetworkEntriesChanged(listOf(first, second))
        testee.onSitesVisitedChanged(31)
        assertFalse(testee.viewState.value!!.shouldShowTrackerNetworkLeaderboard)
    }

    @Test
    fun whenNetworkCountIsTwoAndTotalSitesIsThirtyThenShowSummaryIsFalse() {
        val first = NetworkLeaderboardEntry("Network1", 5)
        val second = NetworkLeaderboardEntry("Network2", 3)
        testee.onTrackerNetworkEntriesChanged(listOf(first, second))
        testee.onSitesVisitedChanged(30)
        assertFalse(testee.viewState.value!!.shouldShowTrackerNetworkLeaderboard)
    }

    @Test
    fun whenNetworkCountIsThreeAndTotalSitesIsThirtyThenShowSummaryIsFalse() {
        val first = NetworkLeaderboardEntry("Network1", 5)
        val second = NetworkLeaderboardEntry("Network2", 3)
        val third = NetworkLeaderboardEntry("Network3", 3)
        testee.onTrackerNetworkEntriesChanged(listOf(first, second, third))
        testee.onSitesVisitedChanged(30)
        assertFalse(testee.viewState.value!!.shouldShowTrackerNetworkLeaderboard)
    }

    @Test
    fun whenNetworkLeaderboardDataAvailableThenViewStateUpdated() {
        val first = NetworkLeaderboardEntry("Network1", 5)
        val second = NetworkLeaderboardEntry("Network2", 3)
        testee.onTrackerNetworkEntriesChanged(listOf(first, second))

        val viewState = testee.viewState.value!!
        assertEquals(first, viewState.trackerNetworkEntries[0])
        assertEquals(second, viewState.trackerNetworkEntries[1])
    }

    @Test
    fun whenNoNetworkLeaderboardDataThenDefaultValuesAreUsed() {
        testee.onTrackerNetworkEntriesChanged(emptyList())
        val viewState = testee.viewState.value!!
        assertEquals(emptyList<NetworkLeaderboardEntry>(), viewState.trackerNetworkEntries)
        assertFalse(viewState.shouldShowTrackerNetworkLeaderboard)
    }

    private fun site(
        https: HttpsStatus = HttpsStatus.SECURE,
        trackerCount: Int = 0,
        allTrackersBlocked: Boolean = true,
        privacyPractices: PrivacyPractices.Practices = PrivacyPractices.UNKNOWN,
        grade: PrivacyGrade = PrivacyGrade.UNKNOWN,
        improvedGrade: PrivacyGrade = PrivacyGrade.UNKNOWN
    ): Site {
        val site: Site = mock()
        whenever(site.https).thenReturn(https)
        whenever(site.trackerCount).thenReturn(trackerCount)
        whenever(site.allTrackersBlocked).thenReturn(allTrackersBlocked)
        whenever(site.privacyPractices).thenReturn(privacyPractices)
        whenever(site.calculateGrades()).thenReturn(Site.SiteGrades(grade, improvedGrade))
        return site
    }

}