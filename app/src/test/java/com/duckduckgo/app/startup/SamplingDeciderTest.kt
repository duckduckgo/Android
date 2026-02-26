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

package com.duckduckgo.app.startup

import android.annotation.SuppressLint
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.moshi.Moshi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.random.Random

@SuppressLint("DenyListedApi")
class SamplingDeciderTest {
    private val startupMetricsFeature = FakeFeatureToggleFactory.Companion.create(StartupMetricsFeature::class.java)
    private lateinit var moshi: Moshi
    private lateinit var mockRandom: Random
    private lateinit var decider: RealSamplingDecider

    @Before
    fun setup() {
        moshi = Moshi.Builder().build()
        mockRandom = mock()
    }

    @Test
    fun `when samplingRate 0_0 configured then always returns false`() {
        startupMetricsFeature.self().setRawStoredState(Toggle.State(settings = """{"sampling":"0.0"}"""))
        decider = RealSamplingDecider(startupMetricsFeature, moshi, mockRandom)

        assertFalse(decider.shouldSample())
        verifyNoInteractions(mockRandom)
    }

    @Test
    fun `when samplingRate 1_0 configured then always returns true`() {
        startupMetricsFeature.self().setRawStoredState(Toggle.State(settings = """{"sampling":"1.0"}"""))
        decider = RealSamplingDecider(startupMetricsFeature, moshi, mockRandom)

        assertTrue(decider.shouldSample())
        verifyNoInteractions(mockRandom)
    }

    @Test
    fun `when samplingRate 0_5 and random below threshold then returns true`() {
        startupMetricsFeature.self().setRawStoredState(Toggle.State(settings = """{"sampling":"0.5"}"""))
        whenever(mockRandom.nextDouble()).thenReturn(0.3)
        decider = RealSamplingDecider(startupMetricsFeature, moshi, mockRandom)

        assertTrue(decider.shouldSample())
        verify(mockRandom).nextDouble()
    }

    @Test
    fun `when samplingRate 0_5 and random above threshold then returns false`() {
        startupMetricsFeature.self().setRawStoredState(Toggle.State(settings = """{"sampling":"0.5"}"""))
        whenever(mockRandom.nextDouble()).thenReturn(0.7)
        decider = RealSamplingDecider(startupMetricsFeature, moshi, mockRandom)

        assertFalse(decider.shouldSample())
        verify(mockRandom).nextDouble()
    }

    @Test
    fun `when negative samplingRate then treated as 0_0`() {
        startupMetricsFeature.self().setRawStoredState(Toggle.State(settings = """{"sampling":"-0.5"}"""))
        decider = RealSamplingDecider(startupMetricsFeature, moshi, mockRandom)

        assertFalse(decider.shouldSample())
        verifyNoInteractions(mockRandom)
    }

    @Test
    fun `when samplingRate greater than 1_0 then treated as 1_0`() {
        startupMetricsFeature.self().setRawStoredState(Toggle.State(settings = """{"sampling":"1.5"}"""))
        decider = RealSamplingDecider(startupMetricsFeature, moshi, mockRandom)

        assertTrue(decider.shouldSample())
        verifyNoInteractions(mockRandom)
    }
}
