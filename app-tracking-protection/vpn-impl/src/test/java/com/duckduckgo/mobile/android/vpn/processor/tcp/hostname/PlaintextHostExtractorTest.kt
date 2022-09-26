/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.processor.tcp.hostname

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.StandardCharsets

class PlaintextHostExtractorTest {

    private val testee = PlaintextHostHeaderExtractor()

    fun prepareTestByteArray(testPayload: String): ByteArray {
        return testPayload.trimIndent().replace("\n", "\r\n").toByteArray(StandardCharsets.US_ASCII)
    }

    @Test
    fun whenHostAvailableThenHostExtractedSuccessfully() {
        val requestHostAvailable = prepareTestByteArray(
            """
            GET / HTTP/1.1
            Host: example.com
            Connection: Keep-Alive
            Accept-Encoding: gzip
            User-Agent: okhttp/4.3.1
        """
        )
        assertEquals("example.com", testee.extract(requestHostAvailable))
    }

    @Test
    fun whenHostAvailableInUnexpectedOrderThenHostExtractedSuccessfully() {
        val requestHostAvailableOutOfOrder = prepareTestByteArray(
            """
            GET / HTTP/1.1
            Connection: Keep-Alive
            Accept-Encoding: gzip
            Host: example.com
            User-Agent: okhttp/4.3.1
        """
        )
        assertEquals("example.com", testee.extract(requestHostAvailableOutOfOrder))
    }

    @Test
    fun whenHostMissingThenNulReturned() {
        val requestHostMissing = prepareTestByteArray(
            """
            GET / HTTP/1.1
            Connection: Keep-Alive
            Accept-Encoding: gzip
            User-Agent: okhttp/4.3.1
        """
        )
        assertNull(testee.extract(requestHostMissing))
    }

    @Test
    fun whenHostEmptyThenNulReturned() {
        val requestHostEmpty = prepareTestByteArray(
            """
            GET / HTTP/1.1
            Host:
            Connection: Keep-Alive
            Accept-Encoding: gzip
            User-Agent: okhttp/4.3.1
        """
        )
        assertNull(testee.extract(requestHostEmpty))
    }

    @Test
    fun whenMalformedHTTPThenNulReturned() {
        val requestMalformed = prepareTestByteArray(
            """
            GET / HTTP/1.1
            Host:
        """
        )
        assertNull(testee.extract(requestMalformed))
    }
}
