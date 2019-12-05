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

import com.duckduckgo.app.trackerdetection.Client.ClientName.TEMPORARY_WHITELIST
import com.duckduckgo.app.trackerdetection.model.DomainContainer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentDomainClientTest {

    @Test
    fun whenDocumentUrlMatchesDomainThenMatchesIsTrue() {
        val data = listOf(DomainHolder("whitelisteddomain.com"))
        val testee = DocumentDomainClient(CLIENT_NAME, data)
        val result = testee.matches("http://tracker.com/script.js", "whitelisteddomain.com/abc")
        assertTrue(result.matches)
    }

    @Test
    fun whenDocumentUrlIsSubdomainOfDomainThenMatchesIsTrue() {
        val data = listOf(DomainHolder("whitelisteddomain.com"))
        val testee = DocumentDomainClient(CLIENT_NAME, data)
        val result = testee.matches("http://tracker.com/script.js", "subdomain.whitelisteddomain.com/abc")
        assertTrue(result.matches)
    }

    @Test
    fun whenDocumentUrlDoesNotMatchDomainThenMatchesIsFalse() {
        val data = listOf(DomainHolder("whitelisteddomain.com"))
        val testee = DocumentDomainClient(CLIENT_NAME, data)
        val result = testee.matches("http://tracker.com/script.js", "anotherdomain.com/abc")
        assertFalse(result.matches)
    }

    class DomainHolder(override val domain: String) : DomainContainer

    companion object {
        private val CLIENT_NAME = TEMPORARY_WHITELIST
    }
}