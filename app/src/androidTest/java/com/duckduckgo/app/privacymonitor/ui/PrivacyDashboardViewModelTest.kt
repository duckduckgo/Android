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
import android.arch.lifecycle.Observer
import android.support.test.InstrumentationRegistry
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacymonitor.HttpsStatus
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
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

    private lateinit var viewStateObserver: Observer<PrivacyDashboardViewModel.ViewState>
    private lateinit var monitor: PrivacyMonitor
    private lateinit var settingStore: PrivacySettingsStore
    private var terms = TermsOfService()

    private val testee: PrivacyDashboardViewModel by lazy {
        val model = PrivacyDashboardViewModel(InstrumentationRegistry.getTargetContext(), settingStore)
        model.viewState.observeForever(viewStateObserver)
        model
    }

    @Before
    fun before() {
        viewStateObserver = mock()
        monitor = mock()
        settingStore = mock()
        whenever(monitor.https).thenReturn(HttpsStatus.SECURE)
        whenever(monitor.termsOfService).thenReturn(terms)
    }

    @After
    fun after() {
        testee.viewState.removeObserver(viewStateObserver)
        settingStore = mock()
    }

    @Test
    fun whenPrivacyInitiallyOnAndSwitchedOffThenShouldReloadIsTrue() {
        whenever(settingStore.privacyOn)
                .thenReturn(true)
                .thenReturn(false)
        assertTrue(testee.shouldReloadPage)
    }

    @Test
    fun whenPrivacyInitiallyOnAndUnchangedThenShouldReloadIsFalse() {
        whenever(settingStore.privacyOn).thenReturn(true)
        assertFalse(testee.shouldReloadPage)
    }

    @Test
    fun whenPrivacyInitiallyOffAndSwitchedOnThenShouldReloadIsTrue() {
        whenever(settingStore.privacyOn)
                .thenReturn(false)
                .thenReturn(true)
        assertTrue(testee.shouldReloadPage)
    }

    @Test
    fun whenPrivacyInitiallyOffAndUnchangedThenShouldReloadIsFalse() {
        whenever(settingStore.privacyOn).thenReturn(false)
        assertFalse(testee.shouldReloadPage)
    }

    @Test
    fun whenHttpsStatusIsSecureThenTextAndIconReflectSame() {
        whenever(monitor.https).thenReturn(HttpsStatus.SECURE)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(getStringResource(R.string.httpsGood), testee.viewState.value?.httpsText)
        assertEquals(R.drawable.dashboard_https_good, testee.viewState.value?.httpsIcon)
    }

    @Test
    fun whenHttpsStatusIsMixedThenTextAndIconReflectSame() {
        whenever(monitor.https).thenReturn(HttpsStatus.MIXED)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(getStringResource(R.string.httpsMixed), testee.viewState.value?.httpsText)
        assertEquals(R.drawable.dashboard_https_neutral, testee.viewState.value?.httpsIcon)
    }

    @Test
    fun whenHttpsStatusIsNoneThenTextAndIconReflectSame() {
        whenever(monitor.https).thenReturn(HttpsStatus.NONE)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(getStringResource(R.string.httpsBad), testee.viewState.value?.httpsText)
        assertEquals(R.drawable.dashboard_https_bad, testee.viewState.value?.httpsIcon)
    }

    @Test
    fun whenNoTrackersNetworksThenNetworkIconIsGood() {
        whenever(monitor.networkCount).thenReturn(0)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(R.drawable.dashboard_networks_good, testee.viewState.value?.networksIcon)
    }

    @Test
    fun whenTenTrackerNetworksAndAllBlockedThenNetworkIconIsGood() {
        whenever(monitor.networkCount).thenReturn(10)
        whenever(monitor.allTrackersBlocked).thenReturn(true)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(R.drawable.dashboard_networks_good, testee.viewState.value?.networksIcon)
    }

    @Test
    fun whenTenTrackerNetworksAndNotAllBlockedThenNetworkIconIsBad() {
        whenever(monitor.networkCount).thenReturn(10)
        whenever(monitor.allTrackersBlocked).thenReturn(false)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(R.drawable.dashboard_networks_bad, testee.viewState.value?.networksIcon)
    }

    @Test
    fun whenNoMajorTrackersNetworksThenMajorNetworkIconIsGood() {
        whenever(monitor.majorNetworkCount).thenReturn(0)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(R.drawable.dashboard_major_networks_good, testee.viewState.value?.majorNetworksIcon)
    }

    @Test
    fun whenTenMajorTrackerNetworksAndAllBlockedThenMajorNetworkIconIsGood() {
        whenever(monitor.majorNetworkCount).thenReturn(10)
        whenever(monitor.allTrackersBlocked).thenReturn(true)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(R.drawable.dashboard_major_networks_good, testee.viewState.value?.majorNetworksIcon)
    }

    @Test
    fun whenTenMajorTrackerNetworksAndNotAllBlockedThenMajorNetworkIconIsBad() {
        whenever(monitor.majorNetworkCount).thenReturn(10)
        whenever(monitor.allTrackersBlocked).thenReturn(false)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(R.drawable.dashboard_major_networks_bad, testee.viewState.value?.majorNetworksIcon)
    }

    @Test
    fun whenNoTrackerNetworksThenNetworkTextShowsZeroBlocked() {
        whenever(monitor.networkCount).thenReturn(0)
        whenever(monitor.allTrackersBlocked).thenReturn(true)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals("0 Tracker Networks Blocked", testee.viewState.value?.networksText)
    }

    @Test
    fun whenTenTrackerNetworksAndAllBlockedThenNetworkTextShowsTenBlocked() {
        whenever(monitor.networkCount).thenReturn(10)
        whenever(monitor.allTrackersBlocked).thenReturn(true)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals("10 Tracker Networks Blocked", testee.viewState.value?.networksText)
    }

    @Test
    fun whenTenTrackersNetworksAndNotAllBlockedThenNetworkTextShowsTenFound() {
        whenever(monitor.networkCount).thenReturn(10)
        whenever(monitor.allTrackersBlocked).thenReturn(false)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals("10 Tracker Networks Found", testee.viewState.value?.networksText)
    }

    @Test
    fun whenNoMajorTrackersNetworksThenMajorNetworkTextShowsZeroBlocked() {
        whenever(monitor.majorNetworkCount).thenReturn(0)
        whenever(monitor.allTrackersBlocked).thenReturn(true)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals("0 Major Tracker Networks Blocked", testee.viewState.value?.majorNetworksText)
    }

    @Test
    fun whenTenMajorTrackerNetworksAndAllBlockedThenMajorNetworkTextShowsTenBlocked() {
        whenever(monitor.majorNetworkCount).thenReturn(10)
        whenever(monitor.allTrackersBlocked).thenReturn(true)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals("10 Major Tracker Networks Blocked", testee.viewState.value?.majorNetworksText)
    }

    @Test
    fun whenTenMajorTrackerNetworksAndNotAllBlockedThenMajorNetworkTextShowsTenFound() {
        whenever(monitor.majorNetworkCount).thenReturn(10)
        whenever(monitor.allTrackersBlocked).thenReturn(false)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals("10 Major Tracker Networks Found", testee.viewState.value?.majorNetworksText)
    }

    @Test
    fun whenNoDataThenDefaultValuesAreUsed() {
        val viewState = testee.viewState.value!!
        assertEquals(getStringResource(R.string.httpsGood), viewState.httpsText)
        assertEquals(R.drawable.dashboard_networks_good, testee.viewState.value?.networksIcon)
        assertEquals("0 Tracker Networks Blocked", testee.viewState.value?.networksText)
        assertEquals(R.drawable.dashboard_major_networks_good, testee.viewState.value?.majorNetworksIcon)
        assertEquals("0 Major Tracker Networks Blocked", testee.viewState.value?.majorNetworksText)
    }

    @Test
    fun whenTermsAreGoodThenTextAndIconReflectSame() {
        terms = TermsOfService(classification = "A", goodPrivacyTerms = listOf("good"))
        whenever(terms.practices).thenReturn(TermsOfService.GOOD)
        assertEquals(getStringResource(R.string.termsGood), testee.viewState.value?.termsText)
        assertEquals(R.drawable.dashboard_terms_good, testee.viewState.value?.termsIcon)
    }

    @Test
    fun whenTermsArePoorThenTextAndIconReflectSame() {
        terms = TermsOfService(classification = "E", badPrivacyTerms = listOf("bad"))
        whenever(terms.practices).thenReturn(TermsOfService.POOR)
        assertEquals(getStringResource(R.string.termsBad), testee.viewState.value?.termsText)
        assertEquals(R.drawable.dashboard_terms_bad, testee.viewState.value?.termsIcon)
    }

    @Test
    fun whenTermsAreMixedThenTextAndIconReflectSame() {
        terms = TermsOfService(goodPrivacyTerms = listOf("good"), badPrivacyTerms = listOf("bad"))
        assertEquals(getStringResource(R.string.termsMixed), testee.viewState.value?.termsText)
        assertEquals(R.drawable.dashboard_terms_neutral, testee.viewState.value?.termsIcon)
    }

    @Test
    fun whenTermsAreUnknownThenTextAndIconReflectSame() {
        assertEquals(getStringResource(R.string.termsUnknown), testee.viewState.value?.termsText)
        assertEquals(R.drawable.dashboard_terms_neutral, testee.viewState.value?.termsIcon)
    }

    private fun getStringResource(id: Int): String =
            InstrumentationRegistry.getTargetContext().getString(id)
}