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

import com.duckduckgo.app.trackerdetection.model.DisconnectTracker
import com.duckduckgo.app.trackerdetection.model.NetworkTrackers
import com.duckduckgo.app.trackerdetection.model.ResourceType
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import java.util.*


class TrackerDetectorInstrumentationTest {

    private val networkTrackers = NetworkTrackers()
    private val trackerDetector = TrackerDetector(networkTrackers)

    companion object {
        private val resourceType = ResourceType.UNKNOWN
        private val network = "Network"
    }

    @Test
    fun whenThereAreNoClientsThenShouldBlockIsFalse() {
        assertFalse(trackerDetector.shouldBlock("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenAllClientsFailToMatchThenShouldBlockIsFalse() {
        trackerDetector.addClient(neverMatchingClient())
        trackerDetector.addClient(neverMatchingClient())
        assertFalse(trackerDetector.shouldBlock("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenAllClientsMatchThenShouldBlockIsTrue() {
        trackerDetector.addClient(alwaysMatchingClient())
        trackerDetector.addClient(alwaysMatchingClient())
        assertTrue(trackerDetector.shouldBlock("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenSomeClientsMatchThenShouldBlockIsTrue() {
        trackerDetector.addClient(neverMatchingClient())
        trackerDetector.addClient(alwaysMatchingClient())
        assertTrue(trackerDetector.shouldBlock("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenUrlHasSameDomainAsDocumentThenShouldBlockIsFalse() {
        trackerDetector.addClient(alwaysMatchingClient())
        assertFalse(trackerDetector.shouldBlock("http://example.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenUrlIsSubdomainOfDocumentThenShouldBlockIsFalse() {
        trackerDetector.addClient(alwaysMatchingClient())
        assertFalse(trackerDetector.shouldBlock("http://mobile.example.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenUrlIsParentOfDocumentThenShouldBlockIsFalse() {
        trackerDetector.addClient(alwaysMatchingClient())
        assertFalse(trackerDetector.shouldBlock("http://example.com/update.js", "http://mobile.example.com/index.com", resourceType))
    }

    @Test
    fun whenUrlIsNetworkOfDocumentThenShouldBlockIsFalse() {
        val networks = Arrays.asList(DisconnectTracker("example.com", "", network, "http://thirdparty.com/"))
        networkTrackers.updateData(networks)
        assertFalse(trackerDetector.shouldBlock("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenDocumentIsNetworkOfUrlThenShouldBlockIsFalse() {
        val networks = Arrays.asList(DisconnectTracker("thirdparty.com", "", network, "http://example.com"))
        networkTrackers.updateData(networks)
        assertFalse(trackerDetector.shouldBlock("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenUrlSharesSameNetworkAsDocumentThenShouldBlockIsFalse() {
        val networks = Arrays.asList(
                DisconnectTracker("thirdparty.com", "", network, "http://network.com"),
                DisconnectTracker("example.com", "", network, "http://network.com")
        )
        networkTrackers.updateData(networks)
        assertFalse(trackerDetector.shouldBlock("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType))
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