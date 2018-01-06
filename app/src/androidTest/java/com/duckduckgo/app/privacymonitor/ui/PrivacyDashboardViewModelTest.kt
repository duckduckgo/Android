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
import com.duckduckgo.app.privacymonitor.model.HttpsStatus
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import com.duckduckgo.app.privacymonitor.model.TermsOfService
import com.duckduckgo.app.privacymonitor.store.PrivacySettingsStore
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PrivacyDashboardViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private var viewStateObserver: Observer<PrivacyDashboardViewModel.ViewState> = mock()
    private var settingStore: PrivacySettingsStore = mock()

    private val testee: PrivacyDashboardViewModel by lazy {
        val model = PrivacyDashboardViewModel(InstrumentationRegistry.getTargetContext(), settingStore)
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
        assertEquals(getStringResource(R.string.httpsGood), viewState.httpsText)
        assertEquals(0, viewState.networkCount)
        assertTrue(viewState.allTrackersBlocked)
        assertEquals(TermsOfService.Practices.UNKNOWN, testee.viewState.value!!.practices)
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
    fun whenFullUpgradeThenHeadingIndicatesUpgrade() {
        val monitor = monitor(allTrackersBlocked = true, trackerCount = 2)
        testee.onPrivacyMonitorChanged(monitor)
        assertTrue(testee.viewState.value!!.heading.contains("ENHANCED FROM"))
    }

    @Test
    fun whenPartialUpgradeAndPrivacyOnThenHeadingIndicatesEnabled() {
        whenever(settingStore.privacyOn).thenReturn(true)
        val monitor = monitor(allTrackersBlocked = false, trackerCount = 2)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(getStringResource(R.string.privacyProtectionEnabled), testee.viewState.value?.heading)
    }

    @Test
    fun whenPartialUpgradeAndPrivacyOffThenHeadingIndicatesDisabled() {
        whenever(settingStore.privacyOn).thenReturn(false)
        val monitor = monitor(allTrackersBlocked = false, trackerCount = 2)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(getStringResource(R.string.privacyProtectionDisabled), testee.viewState.value?.heading)
    }

    @Test
    fun whenNoUpgradeAndPrivacyOnThenHeadingIndicatesEnabled() {
        whenever(settingStore.privacyOn).thenReturn(true)
        val monitor = monitor(allTrackersBlocked = true)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(getStringResource(R.string.privacyProtectionEnabled), testee.viewState.value?.heading)
    }

    @Test
    fun whenNoUpgradeAndPrivacyOffThenHeadingIndicatesDisabled() {
        whenever(settingStore.privacyOn).thenReturn(false)
        val monitor = monitor(allTrackersBlocked = false)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(getStringResource(R.string.privacyProtectionDisabled), testee.viewState.value?.heading)
    }

    @Test
    fun whenPrivacyOnAndNoUpgradeThenHeadingIndicatesEnabled() {
        whenever(settingStore.privacyOn).thenReturn(true)
        assertEquals(getStringResource(R.string.privacyProtectionEnabled), testee.viewState.value?.heading)
    }

    @Test
    fun whenHttpsStatusIsSecureThenTextAndIconReflectSame() {
        val monitor = monitor(https = HttpsStatus.SECURE)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(getStringResource(R.string.httpsGood), testee.viewState.value?.httpsText)
        assertEquals(R.drawable.dashboard_https_good, testee.viewState.value?.httpsIcon)
    }

    @Test
    fun whenHttpsStatusIsMixedThenTextAndIconReflectSame() {
        val monitor = monitor(https = HttpsStatus.MIXED)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(getStringResource(R.string.httpsMixed), testee.viewState.value?.httpsText)
        assertEquals(R.drawable.dashboard_https_neutral, testee.viewState.value?.httpsIcon)
    }

    @Test
    fun whenHttpsStatusIsNoneThenTextAndIconReflectSame() {
        val monitor = monitor(https = HttpsStatus.NONE)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals(getStringResource(R.string.httpsBad), testee.viewState.value?.httpsText)
        assertEquals(R.drawable.dashboard_https_bad, testee.viewState.value?.httpsIcon)
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

    private fun getStringResource(id: Int): String =
            InstrumentationRegistry.getTargetContext().getString(id)
}