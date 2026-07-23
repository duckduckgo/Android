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
        val e = IndeterminateSweepGeometry.calculateEdges(sweepProgress = 0f, seedLeading = 0.5f, pass = SweepPass.FAST)
        assertEquals(0f, e.trailing, 0.0001f)
        assertEquals(0.5f, e.leading, 0.0001f)
    }

    @Test
    fun `unseeded cycle starts at zero width`() {
        for (pass in SweepPass.entries) {
            val e = IndeterminateSweepGeometry.calculateEdges(sweepProgress = 0f, seedLeading = 0f, pass = pass)
            assertEquals(0f, e.trailing, 0.0001f)
            assertEquals(0f, e.leading, 0.0001f)
        }
    }

    @Test
    fun `leading is always at least trailing`() {
        var s = 0f
        while (s < 1f) {
            for (pass in SweepPass.entries) {
                val passSeeds = if (pass == SweepPass.FAST) seeds else listOf(0f)
                for (seed in passSeeds) {
                    val e = IndeterminateSweepGeometry.calculateEdges(s, seed, pass)
                    assertTrue("s=$s seed=$seed pass=$pass", e.leading >= e.trailing - 0.0001f)
                }
            }
            s += 0.01f
        }
    }

    @Test
    fun `segment never spans the full track`() {
        var s = 0f
        while (s < 1f) {
            for (pass in SweepPass.entries) {
                val e = IndeterminateSweepGeometry.calculateEdges(s, seedLeading = 0f, pass = pass)
                val spansFull = e.trailing < 0.02f && e.leading > 0.98f
                assertFalse("full bar at s=$s pass=$pass", spansFull)
            }
            s += 0.005f
        }
    }

    @Test
    fun `leading advances monotonically over a cycle`() {
        for (pass in SweepPass.entries) {
            var prev = -1f
            var s = 0f
            while (s <= 1f) {
                val leading = IndeterminateSweepGeometry.calculateEdges(s, seedLeading = 0f, pass = pass).leading
                assertTrue("regressed at s=$s pass=$pass", leading >= prev - 0.0001f)
                prev = leading
                s += 0.02f
            }
        }
    }

    @Test
    fun `system easing makes the segment visible early in the sweep`() {
        val edges = IndeterminateSweepGeometry.calculateEdges(sweepProgress = 0.25f, seedLeading = 0f, pass = SweepPass.FAST)

        assertTrue(edges.leading > 0.35f)
        assertEquals(0f, edges.trailing, 0.0001f)
    }

    @Test
    fun `slow sweep is longer than fast sweep at midpoint`() {
        val fast = IndeterminateSweepGeometry.calculateEdges(sweepProgress = 0.5f, seedLeading = 0f, pass = SweepPass.FAST)
        val slow = IndeterminateSweepGeometry.calculateEdges(sweepProgress = 0.5f, seedLeading = 0f, pass = SweepPass.SLOW)

        assertTrue(slow.leading - slow.trailing > fast.leading - fast.trailing)
    }

    @Test
    fun `sequence starts with a seeded fast sweep`() {
        val segments = IndeterminateSweepGeometry.calculateSegments(elapsedMs = 0L, cycleMs = 2000L, seedLeading = 0.5f)

        assertEquals(1, segments.size)
        assertEquals(SweepPass.FAST, segments.single().pass)
        assertEquals(0L, segments.single().cycleIndex)
        assertEquals(0.5f, segments.single().edges.leading, 0.0001f)
    }

    @Test
    fun `fast sweep is followed by a slow sweep`() {
        val fast = IndeterminateSweepGeometry.calculateSegments(elapsedMs = 350L, cycleMs = 2000L, seedLeading = 0f).single()
        val gap = IndeterminateSweepGeometry.calculateSegments(elapsedMs = 800L, cycleMs = 2000L, seedLeading = 0f)
        val slow = IndeterminateSweepGeometry.calculateSegments(elapsedMs = 1475L, cycleMs = 2000L, seedLeading = 0f).single()

        assertEquals(SweepPass.FAST, fast.pass)
        assertTrue(gap.isEmpty())
        assertEquals(SweepPass.SLOW, slow.pass)
        assertEquals(fast.edges.leading, slow.edges.leading, 0.0001f)
        assertTrue(slow.edges.leading - slow.edges.trailing > fast.edges.leading - fast.edges.trailing)
    }

    @Test
    fun `slow sweep briefly overlaps the next fast sweep`() {
        val segments = IndeterminateSweepGeometry.calculateSegments(elapsedMs = 2025L, cycleMs = 2000L, seedLeading = 0.5f)

        assertEquals(2, segments.size)
        assertEquals(SweepPass.SLOW, segments[0].pass)
        assertEquals(0L, segments[0].cycleIndex)
        assertEquals(SweepPass.FAST, segments[1].pass)
        assertEquals(1L, segments[1].cycleIndex)
    }

    @Test
    fun `only the first fast sweep is seeded`() {
        val firstFast = IndeterminateSweepGeometry.calculateSegments(elapsedMs = 0L, cycleMs = 2000L, seedLeading = 0.5f)
            .single { it.pass == SweepPass.FAST }
        val secondFast = IndeterminateSweepGeometry.calculateSegments(elapsedMs = 2000L, cycleMs = 2000L, seedLeading = 0.5f)
            .single { it.pass == SweepPass.FAST }

        assertEquals(0.5f, firstFast.edges.leading, 0.0001f)
        assertEquals(0f, secondFast.edges.leading, 0.0001f)
    }

    @Test
    fun `sequenceEnd returns the end of the active slow sweep`() {
        assertEquals(3050L, IndeterminateSweepGeometry.calculateSequenceEnd(startTime = 1000L, now = 1000L, cycleMs = 2000L))
        assertEquals(3050L, IndeterminateSweepGeometry.calculateSequenceEnd(startTime = 1000L, now = 2500L, cycleMs = 2000L))
        assertEquals(5050L, IndeterminateSweepGeometry.calculateSequenceEnd(startTime = 1000L, now = 3000L, cycleMs = 2000L))
        assertEquals(7050L, IndeterminateSweepGeometry.calculateSequenceEnd(startTime = 1000L, now = 5500L, cycleMs = 2000L))
    }

    @Test
    fun `invalid cycle has no segments and finishes immediately`() {
        assertTrue(IndeterminateSweepGeometry.calculateSegments(elapsedMs = 100L, cycleMs = 0L, seedLeading = 0.5f).isEmpty())
        assertEquals(5500L, IndeterminateSweepGeometry.calculateSequenceEnd(startTime = 1000L, now = 5500L, cycleMs = 0L))
    }
}
