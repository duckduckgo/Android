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
import com.duckduckgo.app.trackerdetection.model.ResourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class DisconnectClientInstrumentationTest {

    companion object {
        private const val categoryBanned = "Social"
        private const val categoryAllowed = "Content"
        private const val network = "Network"
        private const val networkUrl = "http://www.network.com/"
        private const val documentUrl = "http://example.com/index.htm"
        private val resourceType = ResourceType.UNKNOWN
    }

    @Test
    fun whenUrlIsATrackerInBlockedCategoryThenMatchesIsTrue() {
        val data = listOf(DisconnectTracker("tracker.com", categoryBanned, network, networkUrl))
        val testee = DisconnectClient(Client.ClientName.DISCONNECT, data)
        assertTrue(testee.matches("http://tracker.com/script.js", documentUrl, resourceType))
    }

    @Test
    fun whenUrlIsATrackerOutsideBlockedCategoryThenMatchesIsFalse() {
        val data = listOf(DisconnectTracker("tracker.com", categoryAllowed, network, networkUrl))
        val testee = DisconnectClient(Client.ClientName.DISCONNECT, data)
        assertFalse(testee.matches("http://tracker.com/script.js", documentUrl, resourceType))
    }

    @Test
    fun whenUrlIsNotATrackerThenMatchesIsFalse() {
        val data = listOf(DisconnectTracker("tracker.com", categoryBanned, network, networkUrl))
        val testee = DisconnectClient(Client.ClientName.DISCONNECT, data)
        assertFalse(testee.matches("http://nontracker.com/script.js", documentUrl, resourceType))
    }

    @Test
    fun whenUrlIsASubdomainOfATrackerThenMatchesIsTrue() {
        val data = listOf(DisconnectTracker("tracker.com", categoryBanned, network, networkUrl))
        val testee = DisconnectClient(Client.ClientName.DISCONNECT, data)
        assertTrue(testee.matches("http://subdomian.tracker.com/script.js", documentUrl, resourceType))
    }

    @Test
    fun whenUrlIsAParentDomainOfATrackerThenMatchesIsFalse() {
        val data = listOf(DisconnectTracker("subdomain.tracker.com", categoryBanned, network, networkUrl))
        val testee = DisconnectClient(Client.ClientName.DISCONNECT, data)
        assertFalse(testee.matches("http://tracker.com/script.js", documentUrl, resourceType))
    }

    @Test
    fun whenUrlContaintsButIsNotSubdomainOfATrackerThenMatchesIsFalse() {
        val data = listOf(DisconnectTracker("tracker.com", categoryBanned, network, networkUrl))
        val testee = DisconnectClient(Client.ClientName.DISCONNECT, data)
        assertFalse(testee.matches("http://notsubdomainoftracker.com", documentUrl, resourceType))
    }
}