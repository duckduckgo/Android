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
import android.net.Uri
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrackerNetworksViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private var viewStateObserver: Observer<TrackerNetworksViewModel.ViewState> = mock()

    private val testee: TrackerNetworksViewModel by lazy {
        val model = TrackerNetworksViewModel()
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
        assertEquals(0, viewState.networkCount)
        assertEquals(true, viewState.allTrackersBlocked)
        assertTrue(viewState.trackingEventsByNetwork.isEmpty())
    }

    @Test
    fun whenUrlIsUpdatedThenViewModelDomainIsUpdated() {
        testee.onPrivacyMonitorChanged(monitor(url = "http://example.com/path"))
        assertEquals("example.com", testee.viewState.value!!.domain)
    }

    @Test
    fun whenNetworkCountIsUpdatedThenViewModelCountUpdated() {
        testee.onPrivacyMonitorChanged(monitor(networkCount = 10))
        val viewState = testee.viewState.value!!
        assertEquals(10, viewState.networkCount)
    }

    @Test
    fun whenTrackersUpdatedThenViewModelTrackersUpdated() {
        val trackersByNetwork = hashMapOf("Network" to arrayListOf(TrackingEvent("", "", null, true)))
        testee.onPrivacyMonitorChanged(monitor(trackersByNetwork = trackersByNetwork))
        val viewState = testee.viewState.value!!
        assertEquals(trackersByNetwork, viewState.trackingEventsByNetwork)
    }

    private fun monitor(url: String = "", networkCount: Int = 0, trackersByNetwork: Map<String, List<TrackingEvent>> = HashMap()): PrivacyMonitor {
        val monitor: PrivacyMonitor = mock()
        whenever(monitor.url).thenReturn(url)
        whenever(monitor.uri).thenReturn(Uri.parse(url))
        whenever(monitor.networkCount).thenReturn(networkCount)
        whenever(monitor.distinctTrackersByNetwork).thenReturn(trackersByNetwork)
        return monitor
    }
}