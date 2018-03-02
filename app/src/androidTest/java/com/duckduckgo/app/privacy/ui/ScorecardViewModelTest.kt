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

package com.duckduckgo.app.privacy.ui

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.TermsOfService
import com.duckduckgo.app.privacy.store.PrivacySettingsStore
import com.duckduckgo.app.trackerdetection.model.TrackerNetwork
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
    fun whenTrackerCountIsUpdatedThenCountIsUpdatedInViewModel() {
        testee.onSiteChanged(site(trackerCount = 10))
        assertEquals(10, testee.viewState.value!!.trackerCount)
    }

    @Test
    fun whenMajorNetworkCountIsUpdatedThenCountIsUpdatedInViewModel() {
        testee.onSiteChanged(site(majorNetworkCount = 10))
        assertEquals(10, testee.viewState.value!!.majorNetworkCount)
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

    @Test
    fun whenIsMemberOfMajorNetworkThenShowIsMemberOfMajorNetworkIsTrue() {
        val site = site(memberNetwork = TrackerNetwork("", "", "", 5, true))
        testee.onSiteChanged(site)
        assertTrue(testee.viewState.value!!.showIsMemberOfMajorNetwork)
    }

    @Test
    fun whenIsNotMemberOfMajorNetworkThenShowIsMemberOfMajorNetworkIsFalse() {
        val site = site(memberNetwork = TrackerNetwork("", "", "", null, false))
        testee.onSiteChanged(site)
        assertFalse(testee.viewState.value!!.showIsMemberOfMajorNetwork)
    }

    @Test
    fun whenIsNotMemberOfAnyNetworkThenShowIsMemberOfMajorNetworkIsFalse() {
        val site = site(memberNetwork = null)
        testee.onSiteChanged(site)
        assertFalse(testee.viewState.value!!.showIsMemberOfMajorNetwork)
    }

    @Test
    fun whenSiteHasDifferentBeforeAndImprovedGradeThenShowEnhancedGradeIsTrue() {
        val site = site(allTrackersBlocked = true, trackerCount = 10)
        testee.onSiteChanged(site)
        assertTrue(testee.viewState.value!!.showEnhancedGrade)
    }

    @Test
    fun whenSiteHasSameBeforeAndImprovedGradeThenShowEnhancedGradeIsFalse() {
        val site = site(allTrackersBlocked = true, trackerCount = 0)
        testee.onSiteChanged(site)
        assertFalse(testee.viewState.value!!.showEnhancedGrade)
    }

    private fun site(https: HttpsStatus = HttpsStatus.SECURE,
                     trackerCount: Int = 0,
                     majorNetworkCount: Int = 0,
                     hasTrackerFromMajorNetwork: Boolean = false,
                     allTrackersBlocked: Boolean = true,
                     terms: TermsOfService = TermsOfService(),
                     memberNetwork: TrackerNetwork? = null): Site {
        val site: Site = mock()
        whenever(site.https).thenReturn(https)
        whenever(site.memberNetwork).thenReturn(memberNetwork)
        whenever(site.trackerCount).thenReturn(trackerCount)
        whenever(site.majorNetworkCount).thenReturn(majorNetworkCount)
        whenever(site.hasTrackerFromMajorNetwork).thenReturn(hasTrackerFromMajorNetwork)
        whenever(site.allTrackersBlocked).thenReturn(allTrackersBlocked)
        whenever(site.termsOfService).thenReturn(terms)
        return site
    }
}