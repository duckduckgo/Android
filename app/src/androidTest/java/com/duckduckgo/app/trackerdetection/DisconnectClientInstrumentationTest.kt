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
        private const val documentUrl = "http://example.com"
        private const val nonTracker = "http://nontracker.com"
        private const val tracker = "http://addthis.com"
        private const val subdomainTracker = "http://subdomain.addthis.com"
        private const val looksLikeTracker = "http://notaddthis.com"
        private val resourceType = ResourceType.UNKNOWN
    }

    @Test
    fun whenUrlIsATrackerInBlockedCategoryThenMatchesIsTrue() {
        val data = listOf(DisconnectTracker(tracker, "Social", "Network", "http://www.network.com/"))
        val testee = DisconnectClient(Client.ClientName.DISCONNECT, data)
        assertTrue(testee.matches(tracker, documentUrl, resourceType))
    }

    @Test
    fun whenUrlIsATrackerOutsideBlockedCategoryThenMatchesIsFalse() {
        val data = listOf(DisconnectTracker(tracker, "Content", "Network", "http://www.network.com/"))
        val testee = DisconnectClient(Client.ClientName.DISCONNECT, data)
        assertFalse(testee.matches(tracker, documentUrl, resourceType))
    }

    @Test
    fun whenUrlIsNotATrackerThenMatchesIsFalse() {
        val data = listOf(DisconnectTracker(tracker, "Social", "Network", "http://www.network.com/"))
        val testee = DisconnectClient(Client.ClientName.DISCONNECT, data)
        assertFalse(testee.matches(nonTracker, documentUrl, resourceType))
    }

    @Test
    fun whenUrlIsASubdomainOfATrackerThenMatchesIsTrue() {
        val data = listOf(DisconnectTracker(tracker, "Social", "Network", "http://www.network.com/"))
        val testee = DisconnectClient(Client.ClientName.DISCONNECT, data)
        assertTrue(testee.matches(subdomainTracker, documentUrl, resourceType))
    }

    @Test
    fun whenUrlIsContaintButIsNotSubdomainOfATrackerThenMatchesIsFalse() {
        val data = listOf(DisconnectTracker(tracker, "Social", "Network", "http://www.network.com/"))
        val testee = DisconnectClient(Client.ClientName.DISCONNECT, data)
        assertFalse(testee.matches(looksLikeTracker, documentUrl, resourceType))
    }

    @Test
    fun whenUrlIsAParentDomainOfATrackerThenMatchesIsFalse() {
        val data = listOf(DisconnectTracker(subdomainTracker, "Social", "Network", "http://www.network.com/"))
        val testee = DisconnectClient(Client.ClientName.DISCONNECT, data)
        assertFalse(testee.matches(tracker, documentUrl, resourceType))
    }

}