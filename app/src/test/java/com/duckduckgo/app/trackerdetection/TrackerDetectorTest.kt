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

import com.duckduckgo.app.trackerdetection.model.ResourceType
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString


class TrackerDetectorTest {

    private val trackerDetector = TrackerDetector()

    companion object {
        private val documentUrl = "http://example.com/index.com"
        private val resourceUrl = "http://somedomain.com/update.js"
        private val resourceType = ResourceType.UNKNOWN
    }

    @Test
    fun whenThereAreNoClientsThenShouldBlockIsFalse() {
        assertFalse(trackerDetector.shouldBlock(resourceUrl, documentUrl, resourceType))
    }

    @Test
    fun whenAllClientsFailToMatchThenShouldBlockIsFalse() {
        trackerDetector.addClient(neverMatchingClient())
        trackerDetector.addClient(neverMatchingClient())
        assertFalse(trackerDetector.shouldBlock(resourceUrl, documentUrl, resourceType))
    }

    @Test
    fun whenAllClientsMatchThenShouldBlockIsTrue() {
        trackerDetector.addClient(alwaysMatchingClient())
        trackerDetector.addClient(alwaysMatchingClient())
        assertTrue(trackerDetector.shouldBlock(resourceUrl, documentUrl, resourceType))
    }

    @Test
    fun whenSomeClientsMatchThenShouldBlockIsTrue() {
        trackerDetector.addClient(neverMatchingClient())
        trackerDetector.addClient(alwaysMatchingClient())
        assertTrue(trackerDetector.shouldBlock(resourceUrl, documentUrl, resourceType))
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