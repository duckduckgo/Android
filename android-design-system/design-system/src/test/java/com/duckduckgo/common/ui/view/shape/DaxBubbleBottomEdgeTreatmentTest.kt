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

package com.duckduckgo.common.ui.view.shape

import com.google.android.material.shape.ShapePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DaxBubbleBottomEdgeTreatmentTest {

    /**
     * Subclasses [ShapePath] so we can record every [cubicToPoint] invocation made by the edge
     * treatment without resorting to Mockito argument captors (which trip over primitive-float
     * matchers).
     */
    private class RecordingShapePath : ShapePath() {
        data class Cubic(
            val x1: Float,
            val y1: Float,
            val x2: Float,
            val y2: Float,
            val x3: Float,
            val y3: Float,
        )
        val cubics = mutableListOf<Cubic>()
        override fun cubicToPoint(
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            x3: Float,
            y3: Float,
        ) {
            cubics.add(Cubic(x1, y1, x2, y2, x3, y3))
            super.cubicToPoint(x1, y1, x2, y2, x3, y3)
        }
    }

    private fun List<RecordingShapePath.Cubic>.allYs(): List<Float> =
        flatMap { listOf(it.y1, it.y2, it.y3) }

    @Test
    fun `default depth fraction is 1`() {
        val treatment = DaxBubbleBottomEdgeTreatment(heightPx = 100)
        assertEquals(1f, treatment.depthFraction, 0.0001f)
    }

    @Test
    fun `depth fraction 0 flattens all tail y coordinates to zero`() {
        val treatment = DaxBubbleBottomEdgeTreatment(heightPx = 100).apply { depthFraction = 0f }
        val shapePath = RecordingShapePath()

        treatment.getEdgePath(length = 400f, center = 200f, interpolation = 1f, shapePath = shapePath)

        assertTrue("Expected at least one cubicToPoint call", shapePath.cubics.isNotEmpty())
        shapePath.cubics.allYs().forEach { y ->
            assertEquals(0f, y, 0.001f)
        }
    }

    @Test
    fun `depth fraction 1 produces non zero tail y coordinates`() {
        val treatment = DaxBubbleBottomEdgeTreatment(heightPx = 100).apply { depthFraction = 1f }
        val shapePath = RecordingShapePath()

        treatment.getEdgePath(length = 400f, center = 200f, interpolation = 1f, shapePath = shapePath)

        val maxAbsY = shapePath.cubics.allYs().maxOf { abs(it) }
        assertTrue("Expected non-zero tail Y values at full depth, got max|Y|=$maxAbsY", maxAbsY > 1f)
    }

    @Test
    fun `depth fraction 0_5 scales y coordinates linearly`() {
        val fullPath = RecordingShapePath()
        DaxBubbleBottomEdgeTreatment(heightPx = 100).apply { depthFraction = 1f }
            .getEdgePath(400f, 200f, 1f, fullPath)

        val halfPath = RecordingShapePath()
        DaxBubbleBottomEdgeTreatment(heightPx = 100).apply { depthFraction = 0.5f }
            .getEdgePath(400f, 200f, 1f, halfPath)

        val fullYs = fullPath.cubics.allYs()
        val halfYs = halfPath.cubics.allYs()
        assertEquals(fullYs.size, halfYs.size)
        fullYs.zip(halfYs).forEach { (full, half) ->
            assertEquals(full * 0.5f, half, 0.01f)
        }
    }
}
