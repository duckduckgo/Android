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

package com.duckduckgo.app.trackerdetection

import com.duckduckgo.app.privacymonitor.store.PrivacySettingsStore
import com.duckduckgo.app.trackerdetection.model.*
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString


class TrackerDetectorTest {

    private val networkTrackers = TrackerNetworks()
    private val settingStore: PrivacySettingsStore = mock()
    private val trackerDetector = TrackerDetector(networkTrackers, settingStore)

    companion object {
        private val resourceType = ResourceType.UNKNOWN
        private val network = "Network"
    }

    @Test
    fun whenThereAreNoClientsThenEvaluateReturnsNull() {
        assertNull(trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenAllClientsFailToMatchThenEvaluateReturnsNull() {
        trackerDetector.addClient(neverMatchingClient())
        trackerDetector.addClient(neverMatchingClient())
        assertNull(trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenPrivacyOnAndAllClientsMatchThenEvaluateReturnsBlockedTrackingEvent() {
        whenever(settingStore.privacyOn).thenReturn(true)
        trackerDetector.addClient(alwaysMatchingClient())
        trackerDetector.addClient(alwaysMatchingClient())
        val expected = TrackingEvent("http://example.com/index.com", "http://thirdparty.com/update.js", null, true)
        val actual = trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType)
        assertEquals(expected, actual)
    }

    @Test
    fun whenPrivacyOffAndAllClientsMatchThenEvaluateReturnsUnblockedTrackingEvent() {
        whenever(settingStore.privacyOn).thenReturn(false)
        trackerDetector.addClient(alwaysMatchingClient())
        trackerDetector.addClient(alwaysMatchingClient())
        val expected = TrackingEvent("http://example.com/index.com", "http://thirdparty.com/update.js", null, false)
        val actual = trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType)
        assertEquals(expected, actual)
    }

    @Test
    fun whenPrivacyOnAndSomeClientsMatchThenEvaluateReturnsBlockedTrackingEvent() {
        whenever(settingStore.privacyOn).thenReturn(true)
        trackerDetector.addClient(neverMatchingClient())
        trackerDetector.addClient(alwaysMatchingClient())
        val expected = TrackingEvent("http://example.com/index.com", "http://thirdparty.com/update.js", null, true)
        val actual = trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType)
        assertEquals(expected, actual)
    }

    @Test
    fun whenPrivacyOffAndSomeClientsMatchThenEvaluateReturnsUnblockedTrackingEvent() {
        whenever(settingStore.privacyOn).thenReturn(false)
        trackerDetector.addClient(neverMatchingClient())
        trackerDetector.addClient(alwaysMatchingClient())
        val expected = TrackingEvent("http://example.com/index.com", "http://thirdparty.com/update.js", null, false)
        val actual = trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType)
        assertEquals(expected, actual)
    }

    @Test
    fun whenTrackerIsPartOfNetworkThenEvaluateReturnsTrackingEventWithNetwork() {
        val networks = arrayListOf(
                DisconnectTracker("thirdparty.com", "category", network, "http://network.com")
        )
        whenever(settingStore.privacyOn).thenReturn(true)
        networkTrackers.updateData(networks)
        trackerDetector.addClient(alwaysMatchingClient())

        val network = TrackerNetwork(network, "http://network.com", category = "category")
        val expected = TrackingEvent("http://example.com/index.com", "http://thirdparty.com/update.js", network, true)
        val actual = trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType)
        assertEquals(expected, actual)
    }

    @Test
    fun whenUrlHasSameDomainAsDocumentThenEvaluateReturnsNull() {
        trackerDetector.addClient(alwaysMatchingClient())
        assertNull(trackerDetector.evaluate("http://example.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenUrlIsSubdomainOfDocumentThenEvaluateReturnsNull() {
        trackerDetector.addClient(alwaysMatchingClient())
        assertNull(trackerDetector.evaluate("http://mobile.example.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenUrlIsParentOfDocumentThenEvaluateReturnsNull() {
        trackerDetector.addClient(alwaysMatchingClient())
        assertNull(trackerDetector.evaluate("http://example.com/update.js", "http://mobile.example.com/index.com", resourceType))
    }

    @Test
    fun whenUrlIsNetworkOfDocumentThenEvaluateReturnsNull() {
        val networks = arrayListOf(DisconnectTracker("example.com", "", network, "http://thirdparty.com/"))
        networkTrackers.updateData(networks)
        assertNull(trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenDocumentIsNetworkOfUrlThenEvaluateReturnsNull() {
        val networks = arrayListOf(DisconnectTracker("thirdparty.com", "", network, "http://example.com"))
        networkTrackers.updateData(networks)
        assertNull(trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenUrlSharesSameNetworkAsDocumentThenEvaluateReturnsNull() {
        val networks = arrayListOf(
                DisconnectTracker("thirdparty.com", "", network, "http://network.com"),
                DisconnectTracker("example.com", "", network, "http://network.com")
        )
        networkTrackers.updateData(networks)
        assertNull(trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType))
    }

    private fun alwaysMatchingClient(): Client {
        val client: Client = mock()
        whenever(client.matches(anyString(), anyString(), any())).thenReturn(true)
        return client
    }

    private fun neverMatchingClient(): Client {
        val client: Client = mock()
        whenever(client.matches(anyString(), anyString(), any())).thenReturn(false)
        return client
    }

}