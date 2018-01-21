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

package com.duckduckgo.app.privacymonitor.ui

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import com.duckduckgo.app.privacymonitor.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacymonitor.db.NetworkPercent
import com.duckduckgo.app.privacymonitor.model.HttpsStatus
import com.duckduckgo.app.privacymonitor.model.PrivacyGrade
import com.duckduckgo.app.privacymonitor.model.TermsOfService
import com.duckduckgo.app.privacymonitor.store.PrivacySettingsStore
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
    fun whenNetworkLeaderboardDataAvailableViewStateUsesIt() {
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
    fun whenMonitorHasTrackersThenViewModelGradesAreUpdated() {
        val monitor = monitor(allTrackersBlocked = true, trackerCount = 1000)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(PrivacyGrade.D, testee.viewState.value?.beforeGrade)
        assertEquals(PrivacyGrade.B, testee.viewState.value?.afterGrade)
    }

    @Test
    fun whenMonitorHttpsStatusIsUpdatedThenViewModelIsUpdated() {
        testee.onPrivacyMonitorChanged(monitor(https = HttpsStatus.MIXED))
        assertEquals(HttpsStatus.MIXED, testee.viewState.value?.httpsStatus)
    }

    @Test
    fun whenNetworkCountIsUpdatedThenCountIsUpdatedInViewModel() {
        testee.onPrivacyMonitorChanged(monitor(networkCount = 10))
        assertEquals(10, testee.viewState.value!!.networkCount)
    }

    @Test
    fun whenAllBlockedUpdatedToFalseThenViewModelIsUpdated() {
        testee.onPrivacyMonitorChanged(monitor(allTrackersBlocked = false))
        assertEquals(false, testee.viewState.value!!.allTrackersBlocked)
    }

    @Test
    fun whenAllBlockedUpdatedToTrueThenViewModelIsUpdated() {
        testee.onPrivacyMonitorChanged(monitor(allTrackersBlocked = true))
        assertEquals(true, testee.viewState.value!!.allTrackersBlocked)
    }

    @Test
    fun whenTermsAreUpdatedThenPracticesAreUpdatedInViewModel() {
        val terms = TermsOfService(classification = "A", goodPrivacyTerms = listOf("good"))
        testee.onPrivacyMonitorChanged(monitor(terms = terms))
        assertEquals(TermsOfService.Practices.GOOD, testee.viewState.value!!.practices)
    }

    private fun monitor(https: HttpsStatus = HttpsStatus.SECURE,
                        trackerCount: Int = 0,
                        networkCount: Int = 0,
                        hasTrackerFromMajorNetwork: Boolean = false,
                        allTrackersBlocked: Boolean = true,
                        terms: TermsOfService = TermsOfService()): PrivacyMonitor {
        val monitor: PrivacyMonitor = mock()
        whenever(monitor.https).thenReturn(https)
        whenever(monitor.trackerCount).thenReturn(trackerCount)
        whenever(monitor.networkCount).thenReturn(networkCount)
        whenever(monitor.hasTrackerFromMajorNetwork).thenReturn(hasTrackerFromMajorNetwork)
        whenever(monitor.allTrackersBlocked).thenReturn(allTrackersBlocked)
        whenever(monitor.termsOfService).thenReturn(terms)
        return monitor
    }

}