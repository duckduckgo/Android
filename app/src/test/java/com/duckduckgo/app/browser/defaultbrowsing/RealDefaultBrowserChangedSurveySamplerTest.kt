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

package com.duckduckgo.app.browser.defaultbrowsing

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.random.Random

class RealDefaultBrowserChangedSurveySamplerTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val toggle: Toggle = mock()
    private val feature: DefaultBrowserChangedSurveyFeature = mock<DefaultBrowserChangedSurveyFeature>().also {
        whenever(it.self()).thenReturn(toggle)
    }

    private fun samplerWith(settings: String?, randomValue: Int): RealDefaultBrowserChangedSurveySampler {
        whenever(toggle.getSettings()).thenReturn(settings)
        return RealDefaultBrowserChangedSurveySampler(
            defaultBrowserChangedSurveyFeature = feature,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            random = FixedRandom(randomValue),
        )
    }

    @Test
    fun whenSettingsAreNullThenDefaultsToFullySampled() = runTest {
        val testee = samplerWith(settings = null, randomValue = 0)
        assertTrue(testee.isInSample())
    }

    @Test
    fun whenSettingsHaveNoSamplingRateKeyThenDefaultsToFullySampled() = runTest {
        val testee = samplerWith(settings = """{"otherKey": 50}""", randomValue = 99)
        assertTrue(testee.isInSample())
    }

    @Test
    fun whenSamplingRateIsFortyAndRandomIsBelowFortyThenInSample() = runTest {
        val testee = samplerWith(settings = """{"samplingRate": 40}""", randomValue = 39)
        assertTrue(testee.isInSample())
    }

    @Test
    fun whenSamplingRateIsFortyAndRandomEqualsFortyThenNotInSample() = runTest {
        val testee = samplerWith(settings = """{"samplingRate": 40}""", randomValue = 40)
        assertFalse(testee.isInSample())
    }

    @Test
    fun whenSamplingRateIsZeroThenNeverInSample() = runTest {
        val testee = samplerWith(settings = """{"samplingRate": 0}""", randomValue = 0)
        assertFalse(testee.isInSample())
    }

    @Test
    fun whenSamplingRateIsHundredThenAlwaysInSample() = runTest {
        val testee = samplerWith(settings = """{"samplingRate": 100}""", randomValue = 99)
        assertTrue(testee.isInSample())
    }

    /** Random subclass returning a fixed value from nextInt(N) for deterministic tests. */
    private class FixedRandom(private val value: Int) : Random() {
        override fun nextBits(bitCount: Int): Int = value
        override fun nextInt(until: Int): Int = value
    }
}
