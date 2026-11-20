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

package com.duckduckgo.app.autocomplete

import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestQueryTimingTest {

    private val timing = SuggestQueryTiming(phase1MarksMillis = listOf(150L, 400L), debounceMillis = 300L)
    private val defaultTiming = SuggestQueryTiming(phase1MarksMillis = listOf(150L, 450L), debounceMillis = 300L)
    private val feature = FakeFeatureToggleFactory.create(AutocompleteSpeedFeature::class.java)
    private val provider = RealSuggestQueryTimingProvider(feature)

    @Test
    fun whenNoSessionThenDebounce() {
        assertEquals(300L, timing.delayMillis(sessionStartedAtMillis = null, nowMillis = 1_000))
    }

    @Test
    fun whenBeforeFirstMarkThenWaitUntilFirstMark() {
        assertEquals(150L, timing.delayMillis(sessionStartedAtMillis = 1_000, nowMillis = 1_000))
        assertEquals(50L, timing.delayMillis(sessionStartedAtMillis = 1_000, nowMillis = 1_100))
    }

    @Test
    fun whenAtOrPastFirstMarkThenWaitUntilSecondMark() {
        assertEquals(250L, timing.delayMillis(sessionStartedAtMillis = 1_000, nowMillis = 1_150))
        assertEquals(10L, timing.delayMillis(sessionStartedAtMillis = 1_000, nowMillis = 1_390))
    }

    @Test
    fun whenPastLastMarkThenDebounce() {
        assertEquals(300L, timing.delayMillis(sessionStartedAtMillis = 1_000, nowMillis = 1_400))
    }

    @Test
    fun whenNoMarksThenDebounce() {
        assertEquals(300L, timing.copy(phase1MarksMillis = emptyList()).delayMillis(sessionStartedAtMillis = 1_000, nowMillis = 1_000))
    }

    @Test
    fun whenFeatureDisabledThenNoTiming() {
        feature.self().setRawStoredState(State(enable = false))

        assertNull(provider.timing())
    }

    @Test
    fun whenEnabledWithoutSettingsThenDefaults() {
        enable()

        assertEquals(defaultTiming, provider.timing())
    }

    @Test
    fun whenSettingsGivenThenUsed() {
        enable(settings = """{"phase1Marks":[150,300,450],"debounceMillis":250}""")

        assertEquals(SuggestQueryTiming(listOf(150L, 300L, 450L), 250L), provider.timing())
    }

    @Test
    fun whenSettingsOutOfRangeThenClampedOrDropped() {
        enable(settings = """{"phase1Marks":[400,10,200,5000,200],"debounceMillis":5}""")

        assertEquals(SuggestQueryTiming(listOf(200L, 400L), 50L), provider.timing())
    }

    @Test
    fun whenSettingsMalformedThenDefaults() {
        enable(settings = "not json")

        assertEquals(defaultTiming, provider.timing())
    }

    private fun enable(settings: String? = null) {
        feature.self().setRawStoredState(State(enable = true, settings = settings))
    }
}
