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

package com.duckduckgo.app.privacymonitor.ui

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import com.duckduckgo.app.privacymonitor.model.HttpsStatus
import com.duckduckgo.app.privacymonitor.model.PrivacyGrade
import com.duckduckgo.app.privacymonitor.model.TermsOfService
import com.duckduckgo.app.privacymonitor.store.PrivacySettingsStore
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ScorecardViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private var viewStateObserver: Observer<ScorecardViewModel.ViewState> = mock()
    private var settingStore: PrivacySettingsStore = mock()

    private val testee: ScorecardViewModel by lazy {
        val model = ScorecardViewModel(settingStore)
        model.viewState.observeForever(viewStateObserver)
        model
    }

    @After
    fun after() {
        testee.viewState.removeObserver(viewStateObserver)
    }

    @Test
    fun whenNoDataThenDefaultValuesAreUsed() {
        val viewState = testee.viewState.value!!
        assertEquals("", viewState.domain)
        assertEquals(PrivacyGrade.UNKNOWN, viewState.beforeGrade)
        assertEquals(PrivacyGrade.UNKNOWN, viewState.afterGrade)
        assertEquals(HttpsStatus.SECURE, viewState.httpsStatus)
        assertEquals(0, viewState.trackerCount)
        assertEquals(0, viewState.majorNetworkCount)
        assertTrue(viewState.allTrackersBlocked)
        assertFalse(viewState.showIsMemberOfMajorNetwork)
        assertFalse(viewState.showEnhancedGrade)
        assertEquals(TermsOfService.Practices.UNKNOWN, testee.viewState.value!!.practices)
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
    fun whenTrackerCountIsUpdatedThenCountIsUpdatedInViewModel() {
        testee.onPrivacyMonitorChanged(monitor(trackerCount = 10))
        assertEquals(10, testee.viewState.value!!.trackerCount)
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

    @Test
    fun whenMonitorHasDifferentBeforeAndImprovedGradeThenShowEnhancedGradeIsTrue() {
        val monitor = monitor(allTrackersBlocked = true, trackerCount = 10)
        testee.onPrivacyMonitorChanged(monitor)
        assertTrue(testee.viewState.value!!.showEnhancedGrade)
    }

    @Test
    fun whenMonitorHasSameBeforeAndImprovedGradeThenShowEnhancedGradeIsFalse() {
        val monitor = monitor(allTrackersBlocked = true, trackerCount = 0)
        testee.onPrivacyMonitorChanged(monitor)
        assertFalse(testee.viewState.value!!.showEnhancedGrade)
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