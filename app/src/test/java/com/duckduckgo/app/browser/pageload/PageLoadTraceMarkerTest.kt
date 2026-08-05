/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.browser.pageload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLoadTraceMarkerTest {

    private val tracer = FakePerfTracer()
    private val testee = PageLoadTraceMarker(tracer)

    @Test
    fun whenHttpPageStartsAndFinishesThenOneBalancedSectionIsEmitted() {
        testee.onPageStarted(HTTPS_URL)
        testee.onPageFinished(HTTPS_URL, 100)

        assertEquals(2, tracer.asyncEvents.size)
        val begin = tracer.asyncEvents[0]
        val end = tracer.asyncEvents[1]
        assertEquals("ddg.pageLoad", begin.name)
        assertTrue(begin.begin)
        assertEquals("ddg.pageLoad", end.name)
        assertTrue(!end.begin)
        assertEquals(begin.cookie, end.cookie)
    }

    @Test
    fun whenPageStartsWithNonHttpUrlThenNothingIsEmitted() {
        testee.onPageStarted("about:blank")
        testee.onPageStarted(null)
        testee.onPageStarted("duck://player/1234")

        assertTrue(tracer.asyncEvents.isEmpty())
    }

    @Test
    fun whenPageFinishesBelowFullProgressThenSectionStaysOpen() {
        testee.onPageStarted(HTTPS_URL)
        testee.onPageFinished(HTTPS_URL, 50)

        assertEquals(1, tracer.asyncEvents.size)
        assertTrue(tracer.asyncEvents.single().begin)
    }

    @Test
    fun whenNonHttpPageFinishesThenOpenHttpSectionIsNotClosed() {
        testee.onPageStarted(HTTPS_URL)
        testee.onPageFinished("about:blank", 100)

        assertEquals(1, tracer.asyncEvents.size)
        assertTrue(tracer.asyncEvents.single().begin)
    }

    @Test
    fun whenPreviousLoadNeverFinishedThenNextStartClosesTheStuckSection() {
        testee.onPageStarted(HTTPS_URL)
        testee.onPageStarted(OTHER_HTTPS_URL)

        assertEquals(3, tracer.asyncEvents.size)
        val firstCookie = tracer.asyncEvents[0].cookie
        assertTrue(!tracer.asyncEvents[1].begin)
        assertEquals(firstCookie, tracer.asyncEvents[1].cookie)
        assertTrue(tracer.asyncEvents[2].begin)
    }

    @Test
    fun whenSecondFinishArrivesForSameLoadThenNoDuplicateEndIsEmitted() {
        testee.onPageStarted(HTTPS_URL)
        testee.onPageFinished(HTTPS_URL, 100)
        testee.onPageFinished(HTTPS_URL, 100)

        assertEquals(2, tracer.asyncEvents.size)
    }

    @Test
    fun whenPageCommitVisibleThenZeroLengthTickIsEmitted() {
        testee.onPageCommitVisible(HTTPS_URL)

        assertEquals(2, tracer.asyncEvents.size)
        assertEquals("ddg.pageCommitVisible", tracer.asyncEvents[0].name)
        assertTrue(tracer.asyncEvents[0].begin)
        assertEquals("ddg.pageCommitVisible", tracer.asyncEvents[1].name)
        assertTrue(!tracer.asyncEvents[1].begin)
        assertEquals(tracer.asyncEvents[0].cookie, tracer.asyncEvents[1].cookie)
    }

    @Test
    fun whenNonHttpPageCommitsVisibleThenNothingIsEmitted() {
        testee.onPageCommitVisible("about:blank")
        testee.onPageCommitVisible(null)
        testee.onPageCommitVisible("duck://player/1234")

        assertTrue(tracer.asyncEvents.isEmpty())
    }

    companion object {
        private const val HTTPS_URL = "https://example.com"
        private const val OTHER_HTTPS_URL = "https://example.org"
    }
}
