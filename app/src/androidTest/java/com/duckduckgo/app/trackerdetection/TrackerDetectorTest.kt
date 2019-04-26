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

import com.duckduckgo.app.privacy.store.PrivacySettingsStore
import com.duckduckgo.app.trackerdetection.Client.ClientName.EASYLIST
import com.duckduckgo.app.trackerdetection.Client.ClientName.EASYPRIVACY
import com.duckduckgo.app.trackerdetection.model.ResourceType
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString


class TrackerDetectorTest {

    private val mockNetworkTrackers: TrackerNetworks = mock()
    private val settingStore: PrivacySettingsStore = mock()
    private val trackerDetector = TrackerDetectorImpl(mockNetworkTrackers, settingStore)

    companion object {
        private val resourceType = ResourceType.UNKNOWN
    }

    @Test
    fun whenThereAreNoClientsThenClientCountIsZero() {
        assertEquals(0, trackerDetector.clientCount)
    }

    @Test
    fun whenClientAddedThenClientCountIsOne() {
        trackerDetector.addClient(alwaysMatchingClient(EASYLIST))
        assertEquals(1, trackerDetector.clientCount)
    }

    @Test
    fun whenTwoClientsWithDifferentNamesAddedThenCountIsTwo() {
        trackerDetector.addClient(alwaysMatchingClient(EASYLIST))
        trackerDetector.addClient(alwaysMatchingClient(EASYPRIVACY))
        assertEquals(2, trackerDetector.clientCount)
    }

    @Test
    fun whenTwoClientsWithSameNameAddedThenClientIsReplacedAndCountIsStillOne() {
        trackerDetector.addClient(alwaysMatchingClient(EASYLIST))
        assertEquals(1, trackerDetector.clientCount)
        assertNotNull(trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType))

        trackerDetector.addClient(neverMatchingClient(EASYLIST))
        assertEquals(1, trackerDetector.clientCount)
        assertNull(trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenThereAreNoClientsThenEvaluateReturnsNull() {
        assertNull(trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenAllClientsFailToMatchThenEvaluateReturnsNull() {
        trackerDetector.addClient(neverMatchingClient(EASYLIST))
        trackerDetector.addClient(neverMatchingClient(EASYPRIVACY))
        assertNull(trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenPrivacyOnAndAllClientsMatchThenEvaluateReturnsBlockedTrackingEvent() {
        whenever(settingStore.privacyOn).thenReturn(true)
        trackerDetector.addClient(alwaysMatchingClient(EASYLIST))
        trackerDetector.addClient(alwaysMatchingClient(EASYPRIVACY))
        val expected = TrackingEvent("http://example.com/index.com", "http://thirdparty.com/update.js", null, true)
        val actual = trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType)
        assertEquals(expected, actual)
    }

    @Test
    fun whenPrivacyOffAndAllClientsMatchThenEvaluateReturnsUnblockedTrackingEvent() {
        whenever(settingStore.privacyOn).thenReturn(false)
        trackerDetector.addClient(alwaysMatchingClient(EASYLIST))
        trackerDetector.addClient(alwaysMatchingClient(EASYPRIVACY))
        val expected = TrackingEvent("http://example.com/index.com", "http://thirdparty.com/update.js", null, false)
        val actual = trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType)
        assertEquals(expected, actual)
    }

    @Test
    fun whenPrivacyOnAndSomeClientsMatchThenEvaluateReturnsBlockedTrackingEvent() {
        whenever(settingStore.privacyOn).thenReturn(true)
        trackerDetector.addClient(neverMatchingClient(EASYLIST))
        trackerDetector.addClient(alwaysMatchingClient(EASYPRIVACY))
        val expected = TrackingEvent("http://example.com/index.com", "http://thirdparty.com/update.js", null, true)
        val actual = trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType)
        assertEquals(expected, actual)
    }

    @Test
    fun whenPrivacyOffAndSomeClientsMatchThenEvaluateReturnsUnblockedTrackingEvent() {
        whenever(settingStore.privacyOn).thenReturn(false)
        trackerDetector.addClient(neverMatchingClient(EASYLIST))
        trackerDetector.addClient(alwaysMatchingClient(EASYPRIVACY))
        val expected = TrackingEvent("http://example.com/index.com", "http://thirdparty.com/update.js", null, false)
        val actual = trackerDetector.evaluate("http://thirdparty.com/update.js", "http://example.com/index.com", resourceType)
        assertEquals(expected, actual)
    }

    @Test
    fun whenUrlHasSameDomainAsDocumentThenEvaluateReturnsNull() {
        trackerDetector.addClient(alwaysMatchingClient(EASYLIST))
        assertNull(trackerDetector.evaluate("http://example.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenUrlIsSubdomainOfDocumentThenEvaluateReturnsNull() {
        trackerDetector.addClient(alwaysMatchingClient(EASYLIST))
        assertNull(trackerDetector.evaluate("http://mobile.example.com/update.js", "http://example.com/index.com", resourceType))
    }

    @Test
    fun whenUrlIsParentOfDocumentThenEvaluateReturnsNull() {
        trackerDetector.addClient(alwaysMatchingClient(EASYLIST))
        assertNull(trackerDetector.evaluate("http://example.com/update.js", "http://mobile.example.com/index.com", resourceType))
    }

    private fun alwaysMatchingClient(name: Client.ClientName): Client {
        val client: Client = mock()
        whenever(client.name).thenReturn(name)
        whenever(client.matches(anyString(), anyString(), any())).thenReturn(true)
        return client
    }

    private fun neverMatchingClient(name: Client.ClientName): Client {
        val client: Client = mock()
        whenever(client.name).thenReturn(name)
        whenever(client.matches(anyString(), anyString(), any())).thenReturn(false)
        return client
    }
}