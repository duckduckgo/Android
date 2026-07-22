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

package com.duckduckgo.app.browser.progressbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndeterminateSweepGeometryTest {

    private val seeds = listOf(0f, 0.3f, 0.5f, 0.7f, 0.95f)

    @Test
    fun `seeded cycle starts exactly at the current fill`() {
        val e = IndeterminateSweepGeometry.calculateEdges(cycleProgress = 0f, seedLeading = 0.5f)
        assertEquals(0f, e.trailing, 0.0001f)
        assertEquals(0.5f, e.leading, 0.0001f)
    }

    @Test
    fun `unseeded cycle starts at zero width`() {
        val e = IndeterminateSweepGeometry.calculateEdges(cycleProgress = 0f, seedLeading = 0f)
        assertEquals(0f, e.trailing, 0.0001f)
        assertEquals(0f, e.leading, 0.0001f)
    }

    @Test
    fun `leading is always at least trailing`() {
        var s = 0f
        while (s < 1f) {
            for (seed in seeds) {
                val e = IndeterminateSweepGeometry.calculateEdges(s, seed)
                assertTrue("s=$s seed=$seed", e.leading >= e.trailing - 0.0001f)
            }
            s += 0.01f
        }
    }

    @Test
    fun `segment never spans the full track`() {
        var s = 0f
        while (s < 1f) {
            for (seed in seeds) {
                val e = IndeterminateSweepGeometry.calculateEdges(s, seed)
                val spansFull = e.trailing < 0.02f && e.leading > 0.98f
                assertFalse("full bar at s=$s seed=$seed", spansFull)
            }
            s += 0.005f
        }
    }

    @Test
    fun `leading advances monotonically over a cycle`() {
        var prev = -1f
        var s = 0f
        while (s <= 1f) {
            val leading = IndeterminateSweepGeometry.calculateEdges(s, 0f).leading
            assertTrue("regressed at s=$s", leading >= prev - 0.0001f)
            prev = leading
            s += 0.02f
        }
    }

    @Test
    fun `seedForCycle applies the seed only on the first cycle`() {
        assertEquals(0.5f, IndeterminateSweepGeometry.seedForCycle(0L, 2000L, 0.5f), 0.0001f)
        assertEquals(0.5f, IndeterminateSweepGeometry.seedForCycle(1999L, 2000L, 0.5f), 0.0001f)
        assertEquals(0f, IndeterminateSweepGeometry.seedForCycle(2000L, 2000L, 0.5f), 0.0001f)
        assertEquals(0f, IndeterminateSweepGeometry.seedForCycle(5000L, 2000L, 0.5f), 0.0001f)
    }

    @Test
    fun `cycleEnd returns the end of the first cycle when finishing within it`() {
        assertEquals(3000L, IndeterminateSweepGeometry.cycleEnd(startTime = 1000L, now = 1000L, cycleMs = 2000L))
        assertEquals(3000L, IndeterminateSweepGeometry.cycleEnd(startTime = 1000L, now = 2500L, cycleMs = 2000L))
    }

    @Test
    fun `cycleEnd rolls to the next boundary exactly on a boundary`() {
        assertEquals(5000L, IndeterminateSweepGeometry.cycleEnd(startTime = 1000L, now = 3000L, cycleMs = 2000L))
    }

    @Test
    fun `cycleEnd returns the end of the current cycle when finishing in a later cycle`() {
        assertEquals(7000L, IndeterminateSweepGeometry.cycleEnd(startTime = 1000L, now = 5500L, cycleMs = 2000L))
    }

    @Test
    fun `cycleEnd finishes immediately for a non-positive cycle`() {
        assertEquals(5500L, IndeterminateSweepGeometry.cycleEnd(startTime = 1000L, now = 5500L, cycleMs = 0L))
    }
}
