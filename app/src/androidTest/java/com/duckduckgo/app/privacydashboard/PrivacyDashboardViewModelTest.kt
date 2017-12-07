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

package com.duckduckgo.app.privacydashboard

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import android.support.test.InstrumentationRegistry
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacymonitor.HttpsStatus
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PrivacyDashboardViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewStateObserver: Observer<PrivacyDashboardViewModel.ViewState>
    private lateinit var monitor: PrivacyMonitor
    private lateinit var testee: PrivacyDashboardViewModel

    @Before
    fun before() {
        viewStateObserver = mock()
        monitor = mock()
        testee = PrivacyDashboardViewModel(InstrumentationRegistry.getTargetContext())
        testee.viewState.observeForever(viewStateObserver)
        whenever(monitor.https).thenReturn(HttpsStatus.SECURE)
    }

    @After
    fun after() {
        testee.viewState.removeObserver(viewStateObserver)
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
    fun whenNoTrackersNetworksThenNetworkTextShowsZero() {
        whenever(monitor.networkCount).thenReturn(0)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals("0 Tracker Networks Blocked", testee.viewState.value?.networksText)
    }

    @Test
    fun whenTenTrackersNetworksThenNetworkTextShowsTen() {
        whenever(monitor.networkCount).thenReturn(10)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals("10 Tracker Networks Blocked", testee.viewState.value?.networksText)
    }

    @Test
    fun whenNoMajorTrackersNetworksThenMajorNetworkTextShowsZero() {
        whenever(monitor.majorNetworkCount).thenReturn(0)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals("0 Major Tracker Networks Blocked", testee.viewState.value?.majorNetworksText)
    }

    @Test
    fun whenTenMajorTrackersNetworksThenMajorNetworkTextShowsTen() {
        whenever(monitor.majorNetworkCount).thenReturn(10)
        testee.onPrivacyMonitorChanged(monitor)
        assertEquals("10 Major Tracker Networks Blocked", testee.viewState.value?.majorNetworksText)
    }

    private fun getStringResource(id: Int): String =
            InstrumentationRegistry.getTargetContext().getString(id)
}