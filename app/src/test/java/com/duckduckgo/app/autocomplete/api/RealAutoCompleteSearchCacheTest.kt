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

package com.duckduckgo.app.autocomplete.api

import com.duckduckgo.app.autocomplete.AutocompleteSpeedFeature
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class RealAutoCompleteSearchCacheTest {

    private val feature = FakeFeatureToggleFactory.create(AutocompleteSpeedFeature::class.java)
    private val timeProvider = FakeTimeProvider()
    private val results = listOf(AutoCompleteServiceRawResult("foo bar", isNav = false))

    @Test
    fun whenQueryStoredThenReturned() {
        val testee = createTestee()

        testee.put("foo", results)

        assertEquals(results, testee.get("foo"))
    }

    @Test
    fun whenQueryNotStoredThenNull() {
        assertNull(createTestee().get("foo"))
    }

    @Test
    fun whenEntryOlderThanTtlThenNull() {
        val testee = createTestee()
        testee.put("foo", results)

        timeProvider.now += 10 * 60 * 1000L + 1

        assertNull(testee.get("foo"))
    }

    @Test
    fun whenEntryWithinTtlThenReturned() {
        val testee = createTestee()
        testee.put("foo", results)

        timeProvider.now += 10 * 60 * 1000L

        assertEquals(results, testee.get("foo"))
    }

    @Test
    fun whenMoreThanMaxEntriesThenLeastRecentlyUsedEvicted() {
        val testee = createTestee()
        testee.put("first", results)
        testee.put("second", results)
        testee.get("first")

        (1..49).forEach { testee.put("query$it", results) }

        assertEquals(results, testee.get("first"))
        assertNull(testee.get("second"))
    }

    @Test
    fun whenClearedThenEntriesRemoved() {
        val testee = createTestee()
        testee.put("foo", results)

        testee.clear()

        assertNull(testee.get("foo"))
    }

    @Test
    fun whenFeatureDisabledThenNothingCached() {
        feature.cacheSuggestResults().setRawStoredState(State(enable = false))
        val testee = createTestee()

        testee.put("foo", results)

        assertNull(testee.get("foo"))
    }

    private fun createTestee() = RealAutoCompleteSearchCache(feature, timeProvider)

    private class FakeTimeProvider : CurrentTimeProvider {
        var now = 0L

        override fun elapsedRealtime(): Long = now

        override fun currentTimeMillis(): Long = now

        override fun localDateTimeNow(): LocalDateTime = LocalDateTime.now()
    }
}
