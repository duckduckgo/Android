/*
 * Copyright (c) 2019 DuckDuckGo
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
import com.duckduckgo.app.trackerdetection.model.TdsTracker
import com.duckduckgo.app.trackerdetection.model.TdsTracker.Action.BLOCK
import com.duckduckgo.app.trackerdetection.model.TdsTracker.Action.WHITELIST
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class TdsClientTest {

    companion object {
        private const val owner = "A Network Owner"
        private const val documentUrl = "http://example.com/index.htm"
        private val resourceType = ResourceType.UNKNOWN
    }

    @Test
    fun whenUrlMatchesWithActionBlockThenMatchesIsTrue() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, owner))
        val testee = TdsClient(Client.ClientName.TDS, data)
        assertTrue(testee.matches("http://tracker.com/script.js", documentUrl, resourceType))
    }

    @Test
    fun whenUrlMatchesWithActionWhitelistThenMatchesIsFalse() {
        val data = listOf(TdsTracker("tracker.com", WHITELIST, owner))
        val testee = TdsClient(Client.ClientName.TDS, data)
        assertFalse(testee.matches("http://tracker.com/script.js", documentUrl, resourceType))
    }

    @Test
    fun whenUrlIsNotATrackerThenMatchesIsFalse() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, owner))
        val testee = TdsClient(Client.ClientName.TDS, data)
        assertFalse(testee.matches("http://nontracker.com/script.js", documentUrl, resourceType))
    }

    @Test
    fun whenUrlIsASubdomainOfATrackerThenMatchesIsTrue() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, owner))
        val testee = TdsClient(Client.ClientName.TDS, data)
        assertTrue(testee.matches("http://subdomian.tracker.com/script.js", documentUrl, resourceType))
    }

    @Test
    fun whenUrlIsAParentDomainOfATrackerThenMatchesIsFalse() {
        val data = listOf(TdsTracker("subdomain.tracker.com", BLOCK, owner))
        val testee = TdsClient(Client.ClientName.TDS, data)
        assertFalse(testee.matches("http://tracker.com/script.js", documentUrl, resourceType))
    }

    @Test
    fun whenUrlContaintsButIsNotSubdomainOfATrackerThenMatchesIsFalse() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, owner))
        val testee = TdsClient(Client.ClientName.TDS, data)
        assertFalse(testee.matches("http://notsubdomainoftracker.com", documentUrl, resourceType))
    }
}