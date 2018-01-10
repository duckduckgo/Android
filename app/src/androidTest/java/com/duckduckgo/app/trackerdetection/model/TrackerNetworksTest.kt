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

package com.duckduckgo.app.trackerdetection.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackerNetworksTest {

    companion object {
        private const val category = "Social"
        private const val networkName = "NetworkName"
        private const val networkUrl = "http://www.network.com/"
        private const val majorNetworkName = "google"
        private const val majorNetworkUrl = "google.com"
        private const val majorNetworkPercentage = 84
    }

    private val testee = TrackerNetworks()

    @Test
    fun whenUrlMatchesTrackerUrlThenNetworkIsReturned() {
        val data = listOf(DisconnectTracker("tracker.com", category, networkName, networkUrl))
        testee.updateData(data)
        val expected = TrackerNetwork(networkName, networkUrl, category)
        assertEquals(expected, testee.network("http://tracker.com/script.js"))
    }

    @Test
    fun whenUrlMatchesNetworkUrlThenNetworkIsReturned() {
        val data = listOf(DisconnectTracker("tracker.com", category, networkName, networkUrl))
        testee.updateData(data)
        val expected = TrackerNetwork(networkName, networkUrl, category)
        assertEquals(expected, testee.network("http://www.network.com/index.html"))
    }

    @Test
    fun whenUrlDoesNotMatchEitherTrackerOrNetworkUrlThenNullIsReturned() {
        val data = listOf(DisconnectTracker("tracker.com", category, networkName, networkUrl))
        testee.updateData(data)
        assertNull(testee.network("http://example.com/index.html"))
    }

    @Test
    fun whenUrlSubdomainMatchesTrackerUrlThenNetworkIsReturned() {
        val data = listOf(DisconnectTracker("tracker.com", category, networkName, networkUrl))
        testee.updateData(data)
        val expected = TrackerNetwork(networkName, networkUrl, category)
        assertEquals(expected, testee.network("http://subdomain.tracker.com/script.js"))
    }

    @Test
    fun whenUrlSubdomainMatchesNetworkUrlThenNetworkIsReturned() {
        val data = listOf(DisconnectTracker("tracker.com", category, networkName, networkUrl))
        testee.updateData(data)
        val expected = TrackerNetwork(networkName, networkUrl, category)
        assertEquals(expected, testee.network("http://www.subdomain.network.com/index.html"))
    }

    @Test
    fun whenUrlContainsButIsNotSubdomainOfTrackerUrlThenNullIsReturned() {
        val data = listOf(DisconnectTracker("tracker.com", category, networkName, networkUrl))
        testee.updateData(data)
        assertNull(testee.network("http://notsubdomainoftracker.com/script.js"))
    }

    @Test
    fun whenUrlContainsButIsNotSubdomainOfNetworkUrlThenNullIsReturned() {
        val data = listOf(DisconnectTracker("tracker.com", category, networkName, networkUrl))
        testee.updateData(data)
        assertNull(testee.network("http://notsubdomainofnetwork.com/index.html"))
    }

    @Test
    fun whenUrlMatchesTrackerInMajorNetworkThenMajorNetworkIsReturned() {
        val data = listOf(DisconnectTracker("tracker.com", category, majorNetworkName, majorNetworkUrl))
        testee.updateData(data)
        val expected = TrackerNetwork(majorNetworkName, majorNetworkUrl, category, majorNetworkPercentage, true)
        assertEquals(expected, testee.network("http://tracker.com/script.js"))
    }
}