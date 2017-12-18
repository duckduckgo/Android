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
    private lateinit var settingStore: PrivacySettingsStore

    private val testee: PrivacyDashboardViewModel by lazy {
        val model = PrivacyDashboardViewModel(InstrumentationRegistry.getTargetContext(), settingStore)
        model.viewState.observeForever(viewStateObserver)
        model
    }

    @Before
    fun before() {
        viewStateObserver = mock()
        settingStore = mock()
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
    fun whenPrivacyOffThenHeadingIndicatesDisabled() {
        whenever(settingStore.privacyOn).thenReturn(false)
        assertEquals(getStringResource(R.string.privacyProtectionDisabled), testee.viewState.value?.heading)
    }

    @Test
    fun whenPrivacyOnAndFullUpgradeThenHeadingIndicatesUpgrade() {
        whenever(settingStore.privacyOn).thenReturn(true)
        val monitor = monitor()
        whenever(monitor.allTrackersBlocked).thenReturn(true)
        whenever(monitor.majorNetworkCount).thenReturn(2)
        testee.onPrivacyMonitorChanged(monitor)
        assertTrue(testee.viewState.value!!.heading.contains("UPGRADED FROM"))
    }

    @Test
    fun whenPrivacyOnWithOnlyPartialUpgradeThenHeadingIndicatesEnabled() {
        whenever(settingStore.privacyOn).thenReturn(true)
        val monitor = monitor()
        whenever(monitor.allTrackersBlocked).thenReturn(false)
        whenever(monitor.majorNetworkCount).thenReturn(2)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(getStringResource(R.string.privacyProtectionEnabled), testee.viewState.value?.heading)
    }

    @Test
    fun whenPrivacyOnAndNoUpgradeThenHeadingIndicatesEnabled() {
        whenever(settingStore.privacyOn).thenReturn(true)
        assertEquals(getStringResource(R.string.privacyProtectionEnabled), testee.viewState.value?.heading)
    }

    @Test
    fun whenHttpsStatusIsSecureThenTextAndIconReflectSame() {
        val monitor = monitor()
        whenever(monitor.https).thenReturn(HttpsStatus.SECURE)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(getStringResource(R.string.httpsGood), testee.viewState.value?.httpsText)
        assertEquals(R.drawable.dashboard_https_good, testee.viewState.value?.httpsIcon)
    }

    @Test
    fun whenHttpsStatusIsMixedThenTextAndIconReflectSame() {
        val monitor = monitor()
        whenever(monitor.https).thenReturn(HttpsStatus.MIXED)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(getStringResource(R.string.httpsMixed), testee.viewState.value?.httpsText)
        assertEquals(R.drawable.dashboard_https_neutral, testee.viewState.value?.httpsIcon)
    }

    @Test
    fun whenHttpsStatusIsNoneThenTextAndIconReflectSame() {
        val monitor = monitor()
        whenever(monitor.https).thenReturn(HttpsStatus.NONE)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(getStringResource(R.string.httpsBad), testee.viewState.value?.httpsText)
        assertEquals(R.drawable.dashboard_https_bad, testee.viewState.value?.httpsIcon)
    }

    @Test
    fun whenNoTrackersNetworksThenNetworkIconIsGood() {
        val monitor = monitor()
        whenever(monitor.networkCount).thenReturn(0)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(R.drawable.dashboard_networks_good, testee.viewState.value?.networksIcon)
    }

    @Test
    fun whenTenTrackerNetworksAndAllBlockedThenNetworkIconIsGood() {
        val monitor = monitor()
        whenever(monitor.networkCount).thenReturn(10)
        whenever(monitor.allTrackersBlocked).thenReturn(true)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(R.drawable.dashboard_networks_good, testee.viewState.value?.networksIcon)
    }

    @Test
    fun whenTenTrackerNetworksAndNotAllBlockedThenNetworkIconIsBad() {
        val monitor = monitor()
        whenever(monitor.networkCount).thenReturn(10)
        whenever(monitor.allTrackersBlocked).thenReturn(false)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(R.drawable.dashboard_networks_bad, testee.viewState.value?.networksIcon)
    }

    @Test
    fun whenNoMajorTrackersNetworksThenMajorNetworkIconIsGood() {
        val monitor = monitor()
        whenever(monitor.majorNetworkCount).thenReturn(0)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(R.drawable.dashboard_major_networks_good, testee.viewState.value?.majorNetworksIcon)
    }

    @Test
    fun whenTenMajorTrackerNetworksAndAllBlockedThenMajorNetworkIconIsGood() {
        val monitor = monitor()
        whenever(monitor.majorNetworkCount).thenReturn(10)
        whenever(monitor.allTrackersBlocked).thenReturn(true)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(R.drawable.dashboard_major_networks_good, testee.viewState.value?.majorNetworksIcon)
    }

    @Test
    fun whenTenMajorTrackerNetworksAndNotAllBlockedThenMajorNetworkIconIsBad() {
        val monitor = monitor()
        whenever(monitor.majorNetworkCount).thenReturn(10)
        whenever(monitor.allTrackersBlocked).thenReturn(false)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(R.drawable.dashboard_major_networks_bad, testee.viewState.value?.majorNetworksIcon)
    }

    @Test
    fun whenNoTrackerNetworksThenNetworkTextShowsZeroBlocked() {
        val monitor = monitor()
        whenever(monitor.networkCount).thenReturn(0)
        whenever(monitor.allTrackersBlocked).thenReturn(true)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals("0 Tracker Networks Blocked", testee.viewState.value?.networksText)
    }

    @Test
    fun whenTenTrackerNetworksAndAllBlockedThenNetworkTextShowsTenBlocked() {
        val monitor = monitor()
        whenever(monitor.networkCount).thenReturn(10)
        whenever(monitor.allTrackersBlocked).thenReturn(true)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals("10 Tracker Networks Blocked", testee.viewState.value?.networksText)
    }

    @Test
    fun whenTenTrackersNetworksAndNotAllBlockedThenNetworkTextShowsTenFound() {
        val monitor = monitor()
        whenever(monitor.networkCount).thenReturn(10)
        whenever(monitor.allTrackersBlocked).thenReturn(false)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals("10 Tracker Networks Found", testee.viewState.value?.networksText)
    }

    @Test
    fun whenNoMajorTrackersNetworksThenMajorNetworkTextShowsZeroBlocked() {
        val monitor = monitor()
        whenever(monitor.majorNetworkCount).thenReturn(0)
        whenever(monitor.allTrackersBlocked).thenReturn(true)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals("0 Major Tracker Networks Blocked", testee.viewState.value?.majorNetworksText)
    }

    @Test
    fun whenTenMajorTrackerNetworksAndAllBlockedThenMajorNetworkTextShowsTenBlocked() {
        val monitor = monitor()
        whenever(monitor.majorNetworkCount).thenReturn(10)
        whenever(monitor.allTrackersBlocked).thenReturn(true)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals("10 Major Tracker Networks Blocked", testee.viewState.value?.majorNetworksText)
    }

    @Test
    fun whenTenMajorTrackerNetworksAndNotAllBlockedThenMajorNetworkTextShowsTenFound() {
        val monitor = monitor()
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
        val terms = TermsOfService(classification = "A", goodPrivacyTerms = listOf("good"))
        testee.onPrivacyMonitorChanged(monitor(terms))
        assertEquals(getStringResource(R.string.termsGood), testee.viewState.value?.termsText)
        assertEquals(R.drawable.dashboard_terms_good, testee.viewState.value?.termsIcon)
    }

    @Test
    fun whenTermsArePoorThenTextAndIconReflectSame() {
        val terms = TermsOfService(classification = "E", badPrivacyTerms = listOf("bad"))
        testee.onPrivacyMonitorChanged(monitor(terms))
        assertEquals(getStringResource(R.string.termsBad), testee.viewState.value?.termsText)
        assertEquals(R.drawable.dashboard_terms_bad, testee.viewState.value?.termsIcon)
    }

    @Test
    fun whenTermsAreMixedThenTextAndIconReflectSame() {
        val terms = TermsOfService(goodPrivacyTerms = listOf("good"), badPrivacyTerms = listOf("bad"))
        testee.onPrivacyMonitorChanged(monitor(terms))
        assertEquals(getStringResource(R.string.termsMixed), testee.viewState.value?.termsText)
        assertEquals(R.drawable.dashboard_terms_neutral, testee.viewState.value?.termsIcon)
    }

    @Test
    fun whenTermsAreUnknownThenTextAndIconReflectSame() {
        testee.onPrivacyMonitorChanged(monitor())
        assertEquals(getStringResource(R.string.termsUnknown), testee.viewState.value?.termsText)
        assertEquals(R.drawable.dashboard_terms_neutral, testee.viewState.value?.termsIcon)
    }

    private fun monitor(terms: TermsOfService = TermsOfService()): PrivacyMonitor {
        val monitor: PrivacyMonitor = mock()
        whenever(monitor.https).thenReturn(HttpsStatus.SECURE)
        whenever(monitor.termsOfService).thenReturn(terms)
        return monitor
    }

    private fun getStringResource(id: Int): String =
            InstrumentationRegistry.getTargetContext().getString(id)
}