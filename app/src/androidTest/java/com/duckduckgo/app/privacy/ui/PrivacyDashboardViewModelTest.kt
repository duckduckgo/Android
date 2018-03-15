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

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.NetworkPercent
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.TermsOfService
import com.duckduckgo.app.privacy.store.PrivacySettingsStore
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
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
    private var networkLeaderboard: NetworkLeaderboardDao = mock()
    private var networkPercentsLiveData: LiveData<Array<NetworkPercent>> = mock()

    private val testee: PrivacyDashboardViewModel by lazy {
        val model = PrivacyDashboardViewModel(settingStore, networkLeaderboard)
        model.viewState.observeForever(viewStateObserver)
        model
    }

    @Before
    fun before() {
        whenever(networkPercentsLiveData.value).thenReturn(emptyArray())
        whenever(networkLeaderboard.networkPercents()).thenReturn(networkPercentsLiveData)
    }

    @After
    fun after() {
        testee.viewState.removeObserver(viewStateObserver)
        testee.onCleared()
    }

    @Test
    fun whenNetworkLeaderboardDataAvailableViewStateUpdated() {
        testee.onNetworkPercentsChanged(arrayOf(
                NetworkPercent("Network1", 1.0f, 20),
                NetworkPercent("Network2", 2.0f, 20),
                NetworkPercent("Network3", 3.0f, 20)))

        val viewState = testee.viewState.value!!
        assertEquals("Network1", viewState.networkTrackerSummaryName1)
        assertEquals("Network2", viewState.networkTrackerSummaryName2)
        assertEquals("Network3", viewState.networkTrackerSummaryName3)
        assertEquals(1.0f, viewState.networkTrackerSummaryPercent1)
        assertEquals(2.0f, viewState.networkTrackerSummaryPercent2)
        assertEquals(3.0f, viewState.networkTrackerSummaryPercent3)
    }

    @Test
    fun whenNoDataNetworkLeaderboardViewStateIsDefault() {
        testee.onNetworkPercentsChanged(emptyArray())

        val viewState = testee.viewState.value!!
        assertNull(viewState.networkTrackerSummaryName1)
        assertNull(viewState.networkTrackerSummaryName2)
        assertNull(viewState.networkTrackerSummaryName3)
        assertEquals(0.0f, viewState.networkTrackerSummaryPercent1)
        assertEquals(0.0f, viewState.networkTrackerSummaryPercent2)
        assertEquals(0.0f, viewState.networkTrackerSummaryPercent3)
    }

    @Test
    fun whenAtLeastThreeNetworksAndThirtyDomainsVisitedThenShowNetworkSummaryIsTrue() {
        testee.onNetworkPercentsChanged(arrayOf(
            NetworkPercent("Network1", 1.0f, 28),
            NetworkPercent("Network2", 2.0f, 1),
            NetworkPercent("Network3", 3.0f, 1)))
        assertTrue(testee.viewState.value!!.showNetworkTrackerSummary)
    }

    @Test
    fun whenLessThanThreeNetworksVisitedThenShowNetworkSummaryIsFalse() {
        testee.onNetworkPercentsChanged(arrayOf(
            NetworkPercent("Network1", 1.0f, 29),
            NetworkPercent("Network2", 2.0f, 1)))
        assertFalse(testee.viewState.value!!.showNetworkTrackerSummary)
    }

    @Test
    fun whenLessThanThirtyNetworksVisitedThenShowNetworkSummaryIsFalse() {
        testee.onNetworkPercentsChanged(arrayOf(
            NetworkPercent("Network1", 1.0f, 27),
            NetworkPercent("Network2", 2.0f, 1),
            NetworkPercent("Network3", 3.0f, 1)))
        assertFalse(testee.viewState.value!!.showNetworkTrackerSummary)
    }

    @Test
    fun whenNoDataThenDefaultValuesAreUsed() {
        val viewState = testee.viewState.value!!
        assertEquals("", viewState.domain)
        assertEquals(PrivacyGrade.UNKNOWN, viewState.beforeGrade)
        assertEquals(PrivacyGrade.UNKNOWN, viewState.afterGrade)
        assertEquals(HttpsStatus.SECURE, viewState.httpsStatus)
        assertEquals(0, viewState.networkCount)
        assertTrue(viewState.allTrackersBlocked)
        assertEquals(TermsOfService.Practices.UNKNOWN, testee.viewState.value!!.practices)
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
    fun whenSiteHasTrackersThenViewModelGradesAreUpdated() {
        val site = site(allTrackersBlocked = true, trackerCount = 1000)
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
    fun whenNetworkCountIsUpdatedThenCountIsUpdatedInViewModel() {
        testee.onSiteChanged(site(networkCount = 10))
        assertEquals(10, testee.viewState.value!!.networkCount)
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
    fun whenTermsAreUpdatedThenPracticesAreUpdatedInViewModel() {
        val terms = TermsOfService(classification = "A", goodPrivacyTerms = listOf("good"))
        testee.onSiteChanged(site(terms = terms))
        assertEquals(TermsOfService.Practices.GOOD, testee.viewState.value!!.practices)
    }



    private fun site(https: HttpsStatus = HttpsStatus.SECURE,
                     trackerCount: Int = 0,
                     networkCount: Int = 0,
                     hasTrackerFromMajorNetwork: Boolean = false,
                     allTrackersBlocked: Boolean = true,
                     terms: TermsOfService = TermsOfService()): Site {
        val site: Site = mock()
        whenever(site.https).thenReturn(https)
        whenever(site.trackerCount).thenReturn(trackerCount)
        whenever(site.networkCount).thenReturn(networkCount)
        whenever(site.hasTrackerFromMajorNetwork).thenReturn(hasTrackerFromMajorNetwork)
        whenever(site.allTrackersBlocked).thenReturn(allTrackersBlocked)
        whenever(site.termsOfService).thenReturn(terms)
        return site
    }

}