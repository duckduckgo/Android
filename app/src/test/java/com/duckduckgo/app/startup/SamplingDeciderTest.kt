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

class SamplingDeciderTest {
    private lateinit var moshi: Moshi
    private lateinit var mockRandom: Random
    private lateinit var decider: RealSamplingDecider

    @Before
    fun setup() {
        moshi = Moshi.Builder().build()
        mockRandom = mock()
        decider = RealSamplingDecider(moshi, mockRandom)
    }

    @Test
    fun `when samplingRate 0_0 configured then always returns false`() {
        assertFalse(decider.shouldSample("""{"sampling":0.0}"""))
        verifyNoInteractions(mockRandom)
    }

    @Test
    fun `when samplingRate 1_0 configured then always returns true`() {
        assertTrue(decider.shouldSample("""{"sampling":1.0}"""))
        verifyNoInteractions(mockRandom)
    }

    @Test
    fun `when samplingRate 0_5 and random below threshold then returns true`() {
        whenever(mockRandom.nextDouble()).thenReturn(0.3)

        assertTrue(decider.shouldSample("""{"sampling":0.5}"""))
        verify(mockRandom).nextDouble()
    }

    @Test
    fun `when samplingRate 0_5 and random above threshold then returns false`() {
        whenever(mockRandom.nextDouble()).thenReturn(0.7)

        assertFalse(decider.shouldSample("""{"sampling":0.5}"""))
        verify(mockRandom).nextDouble()
    }

    @Test
    fun `when negative samplingRate then treated as 0_0`() {
        assertFalse(decider.shouldSample("""{"sampling":-0.5}"""))
        verifyNoInteractions(mockRandom)
    }

    @Test
    fun `when samplingRate greater than 1_0 then treated as 1_0`() {
        assertTrue(decider.shouldSample("""{"sampling":1.5}"""))
        verifyNoInteractions(mockRandom)
    }

    @Test
    fun `when settings JSON is null then falls back to default 1 percent sampling`() {
        whenever(mockRandom.nextDouble()).thenReturn(0.005)

        assertTrue(decider.shouldSample(null))
        verify(mockRandom).nextDouble()
    }

    @Test
    fun `when settings JSON is malformed then falls back to default 1 percent sampling`() {
        whenever(mockRandom.nextDouble()).thenReturn(0.005)

        assertTrue(decider.shouldSample("not-valid-json"))
        verify(mockRandom).nextDouble()
    }

    @Test
    fun `when sampling key is missing then falls back to default 1 percent sampling`() {
        whenever(mockRandom.nextDouble()).thenReturn(0.005)

        assertTrue(decider.shouldSample("{}"))
        verify(mockRandom).nextDouble()
    }
}
