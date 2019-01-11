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

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
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
        assertEquals(true, viewState.allTrackersBlocked)
        assertTrue(viewState.trackingEventsByNetwork.isEmpty())
    }

    @Test
    fun whenUrlIsUpdatedThenViewModelDomainIsUpdated() {
        testee.onSiteChanged(site(url = "http://example.com/path"))
        assertEquals("example.com", testee.viewState.value!!.domain)
    }

    @Test
    fun whenTrackersUpdatedThenViewModelTrackersUpdated() {
        val trackersByNetwork = hashMapOf("Network" to arrayListOf(TrackingEvent("", "", null, true)))
        testee.onSiteChanged(site(trackersByNetwork = trackersByNetwork))
        val viewState = testee.viewState.value!!
        assertEquals(trackersByNetwork, viewState.trackingEventsByNetwork)
    }

    private fun site(url: String = "", networkCount: Int = 0, trackersByNetwork: Map<String, List<TrackingEvent>> = HashMap()): Site {
        val site: Site = mock()
        whenever(site.url).thenReturn(url)
        whenever(site.uri).thenReturn(Uri.parse(url))
        whenever(site.distinctTrackersByNetwork).thenReturn(trackersByNetwork)
        return site
    }
}