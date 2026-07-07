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
import org.junit.Test

class PageLoadTraceMarkerTest {

    private data class Event(val kind: String, val cookie: Int)

    private class FakeTracer : PageLoadTracer {
        val events = mutableListOf<Event>()
        private var seq = 0
        override fun beginAsyncSection(name: String): Int {
            val cookie = seq++
            events += Event("begin", cookie)
            return cookie
        }
        override fun endAsyncSection(name: String, cookie: Int) { events += Event("end", cookie) }
    }

    private val tracer = FakeTracer()
    private val marker = PageLoadTraceMarker(tracer)

    @Test
    fun whenHttpPageLoadsThenBeginsAndEndsWithSameUniqueCookie() {
        marker.onPageStarted("https://example.com")
        marker.onPageFinished("https://example.com", 100)

        assertEquals(listOf(Event("begin", 0), Event("end", 0)), tracer.events)
    }

    @Test
    fun whenTwoLoadsThenCookiesAreUniquePerLoad() {
        marker.onPageStarted("https://a.com")
        marker.onPageFinished("https://a.com", 100)
        marker.onPageStarted("https://b.com")
        marker.onPageFinished("https://b.com", 100)

        assertEquals(
            listOf(Event("begin", 0), Event("end", 0), Event("begin", 1), Event("end", 1)),
            tracer.events,
        )
    }

    @Test
    fun whenNonHttpUrlThenNotInstrumented() {
        marker.onPageStarted("about:blank")
        marker.onPageStarted("duck://newtab")
        marker.onPageFinished("about:blank", 100)

        assertEquals(emptyList<Event>(), tracer.events)
    }

    @Test
    fun whenPreviousSectionLeftOpenThenItIsClosedBeforeNextBegins() {
        marker.onPageStarted("https://a.com") // begin 0, never finishes (stuck)
        marker.onPageStarted("https://b.com") // must close 0, then begin 1

        assertEquals(
            listOf(Event("begin", 0), Event("end", 0), Event("begin", 1)),
            tracer.events,
        )
    }

    @Test
    fun whenProgressNotCompleteThenSectionStaysOpen() {
        marker.onPageStarted("https://a.com")
        marker.onPageFinished("https://a.com", 50)

        assertEquals(listOf(Event("begin", 0)), tracer.events)
    }
}
