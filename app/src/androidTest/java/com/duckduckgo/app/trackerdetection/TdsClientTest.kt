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

import com.duckduckgo.app.trackerdetection.Client.ClientName
import com.duckduckgo.app.trackerdetection.model.ResourceType
import com.duckduckgo.app.trackerdetection.model.TdsTracker
import com.duckduckgo.app.trackerdetection.model.TdsTracker.Action.BLOCK
import com.duckduckgo.app.trackerdetection.model.TdsTracker.Action.WHITELIST
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class TdsClientTest {

    @Test
    fun whenUrlMatchesWithActionBlockThenMatchesIsTrue() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER))
        val testee = TdsClient(ClientName.TDS, data)
        assertTrue(testee.matches("http://tracker.com/script.js", DOCUMENT_URL, RESOURCE_TYPE))
    }

    @Test
    fun whenUrlMatchesWithActionWhitelistThenMatchesIsFalse() {
        val data = listOf(TdsTracker("tracker.com", WHITELIST, OWNER))
        val testee = TdsClient(ClientName.TDS, data)
        assertFalse(testee.matches("http://tracker.com/script.js", DOCUMENT_URL, RESOURCE_TYPE))
    }

    @Test
    fun whenUrlIsNotATrackerThenMatchesIsFalse() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER))
        val testee = TdsClient(ClientName.TDS, data)
        assertFalse(testee.matches("http://nontracker.com/script.js", DOCUMENT_URL, RESOURCE_TYPE))
    }

    @Test
    fun whenUrlIsASubdomainOfATrackerThenMatchesIsTrue() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER))
        val testee = TdsClient(ClientName.TDS, data)
        assertTrue(testee.matches("http://subdomian.tracker.com/script.js", DOCUMENT_URL, RESOURCE_TYPE))
    }

    @Test
    fun whenUrlIsAParentDomainOfATrackerThenMatchesIsFalse() {
        val data = listOf(TdsTracker("subdomain.tracker.com", BLOCK, OWNER))
        val testee = TdsClient(ClientName.TDS, data)
        assertFalse(testee.matches("http://tracker.com/script.js", DOCUMENT_URL, RESOURCE_TYPE))
    }

    @Test
    fun whenUrlContainsButIsNotSubdomainOfATrackerThenMatchesIsFalse() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER))
        val testee = TdsClient(ClientName.TDS, data)
        assertFalse(testee.matches("http://notsubdomainoftracker.com", DOCUMENT_URL, RESOURCE_TYPE))
    }

    companion object {
        private const val OWNER = "A Network Owner"
        private const val DOCUMENT_URL = "http://example.com/index.htm"
        private val RESOURCE_TYPE = ResourceType.UNKNOWN
    }
}