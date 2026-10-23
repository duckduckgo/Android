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

package com.duckduckgo.eventhub.impl.pixels

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WebEventDeduplicatorTest {

    private val deduplicator = WebEventDeduplicator()

    @Test
    fun `first occurrence on a page is delivered and repeats are dropped`() {
        assertTrue(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1))
        assertFalse(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1))
        assertFalse(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1))
    }

    @Test
    fun `different payloads of the same type are separate occurrences`() {
        assertTrue(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1))
        assertTrue(deduplicator.isFirstOccurrence("adwall", JSONObject().put("reason", "redirect"), TAB_1))
    }

    @Test
    fun `different types with the same payload are separate occurrences`() {
        assertTrue(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1))
        assertTrue(deduplicator.isFirstOccurrence("captcha", overlay(), TAB_1))
    }

    @Test
    fun `payloads equal as JSON values are one occurrence`() {
        assertTrue(deduplicator.isFirstOccurrence("adwall", JSONObject("""{"reason": "overlay", "stage": 1}"""), TAB_1))
        assertFalse(deduplicator.isFirstOccurrence("adwall", JSONObject("""{"stage": 1, "reason": "overlay"}"""), TAB_1))
    }

    @Test
    fun `omitted payload and empty object are one occurrence`() {
        assertTrue(deduplicator.isFirstOccurrence("adwall", null, TAB_1))
        assertFalse(deduplicator.isFirstOccurrence("adwall", JSONObject(), TAB_1))
    }

    @Test
    fun `explicit null payload is not the same as an omitted payload`() {
        assertTrue(deduplicator.isFirstOccurrence("adwall", null, TAB_1))
        assertTrue(deduplicator.isFirstOccurrence("adwall", JSONObject.NULL, TAB_1))
    }

    @Test
    fun `non-object payloads keep their own identity`() {
        assertTrue(deduplicator.isFirstOccurrence("adwall", JSONObject(), TAB_1))
        assertTrue(deduplicator.isFirstOccurrence("adwall", JSONArray(), TAB_1))
        assertTrue(deduplicator.isFirstOccurrence("adwall", "overlay", TAB_1))
        assertFalse(deduplicator.isFirstOccurrence("adwall", "overlay", TAB_1))
    }

    @Test
    fun `empty webViewId disables de-duplication`() {
        assertTrue(deduplicator.isFirstOccurrence("adwall", overlay(), ""))
        assertTrue(deduplicator.isFirstOccurrence("adwall", overlay(), ""))
    }

    @Test
    fun `tabs de-duplicate independently`() {
        assertTrue(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1))
        assertTrue(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_2))
        assertFalse(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1))
    }

    @Test
    fun `navigating to a different URL clears the tab`() {
        deduplicator.onNavigation(TAB_1, URL_A)
        deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1)

        assertTrue(deduplicator.onNavigation(TAB_1, URL_B))

        assertTrue(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1))
    }

    @Test
    fun `first navigation of a tab does not clear keys from before it`() {
        deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1)

        assertFalse(deduplicator.onNavigation(TAB_1, URL_A))

        assertFalse(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1))
    }

    @Test
    fun `same-URL navigation does not clear the tab`() {
        deduplicator.onNavigation(TAB_1, URL_A)
        deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1)

        assertFalse(deduplicator.onNavigation(TAB_1, URL_A))

        assertFalse(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1))
    }

    @Test
    fun `returning to a previous URL clears the tab`() {
        deduplicator.onNavigation(TAB_1, URL_A)
        deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1)

        deduplicator.onNavigation(TAB_1, URL_B)
        deduplicator.onNavigation(TAB_1, URL_A)

        assertTrue(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1))
    }

    @Test
    fun `navigation only clears the navigated tab`() {
        deduplicator.onNavigation(TAB_1, URL_A)
        deduplicator.onNavigation(TAB_2, URL_A)
        deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1)
        deduplicator.isFirstOccurrence("adwall", overlay(), TAB_2)

        deduplicator.onNavigation(TAB_1, URL_B)

        assertTrue(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1))
        assertFalse(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_2))
    }

    @Test
    fun `navigation with empty webViewId or URL is ignored`() {
        deduplicator.onNavigation(TAB_1, URL_A)
        deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1)

        deduplicator.onNavigation("", URL_B)
        deduplicator.onNavigation(TAB_1, "")

        assertFalse(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1))
        deduplicator.onNavigation(TAB_1, URL_B)
        assertTrue(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1))
    }

    @Test
    fun `clear forgets every tab and URL`() {
        deduplicator.onNavigation(TAB_1, URL_A)
        deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1)

        deduplicator.clear()

        assertTrue(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1))
        // URL_A was forgotten, so this is the tab's first navigation and keeps the key just recorded
        deduplicator.onNavigation(TAB_1, URL_B)
        assertFalse(deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1))
    }

    @Test
    fun `concurrent reports of the same event deliver exactly one`() {
        val pool = Executors.newFixedThreadPool(THREADS)
        try {
            repeat(ITERATIONS) { iteration ->
                val deduplicator = WebEventDeduplicator()
                val start = CountDownLatch(1)
                val delivered = AtomicInteger()

                val reports = List(THREADS) {
                    pool.submit {
                        start.await()
                        if (deduplicator.isFirstOccurrence("adwall", overlay(), TAB_1)) delivered.incrementAndGet()
                    }
                }
                start.countDown()
                reports.forEach { it.get(5, TimeUnit.SECONDS) }

                assertEquals("iteration $iteration", 1, delivered.get())
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `concurrent navigation and events across tabs keep working`() {
        val pool = Executors.newFixedThreadPool(THREADS)
        try {
            repeat(ITERATIONS) {
                val deduplicator = WebEventDeduplicator()
                val start = CountDownLatch(1)

                val work = List(THREADS) { thread ->
                    pool.submit {
                        start.await()
                        val tab = "tab$thread"
                        repeat(20) { step ->
                            deduplicator.onNavigation(tab, if (step % 2 == 0) URL_A else URL_B)
                            deduplicator.isFirstOccurrence("adwall", overlay(), tab)
                        }
                    }
                }
                start.countDown()
                work.forEach { it.get(5, TimeUnit.SECONDS) }

                (0 until THREADS).forEach { thread ->
                    assertTrue(deduplicator.isFirstOccurrence("captcha", overlay(), "tab$thread"))
                    assertFalse(deduplicator.isFirstOccurrence("captcha", overlay(), "tab$thread"))
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun overlay() = JSONObject().put("reason", "overlay")

    private companion object {
        const val TAB_1 = "tab1"
        const val TAB_2 = "tab2"
        const val URL_A = "https://a.example.com/"
        const val URL_B = "https://b.example.com/"
        const val THREADS = 8
        const val ITERATIONS = 300
    }
}
