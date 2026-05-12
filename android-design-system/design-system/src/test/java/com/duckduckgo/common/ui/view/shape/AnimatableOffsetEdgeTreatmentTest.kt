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

import com.google.android.material.shape.EdgeTreatment
import com.google.android.material.shape.ShapePath
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimatableOffsetEdgeTreatmentTest {

    private class CenterCapturingTreatment : EdgeTreatment() {
        var capturedCenter: Float = Float.NaN

        override fun getEdgePath(
            length: Float,
            center: Float,
            interpolation: Float,
            shapePath: ShapePath,
        ) {
            capturedCenter = center
        }
    }

    @Test
    fun `fraction 0 places center at start position`() {
        val base = CenterCapturingTreatment()
        val treatment = AnimatableOffsetEdgeTreatment(base, offsetFromStartPx = 96f)
        treatment.offsetFromEndPx = 80f
        treatment.fraction = 0f

        val shapePath = ShapePath()
        treatment.getEdgePath(400f, 200f, 1f, shapePath)

        // startCenter = length - offsetFromStartPx = 400 - 96 = 304
        assertEquals(304f, base.capturedCenter, 0.01f)
    }

    @Test
    fun `fraction 1 places center at end position`() {
        val base = CenterCapturingTreatment()
        val treatment = AnimatableOffsetEdgeTreatment(base, offsetFromStartPx = 96f)
        treatment.offsetFromEndPx = 80f
        treatment.fraction = 1f

        val shapePath = ShapePath()
        treatment.getEdgePath(400f, 200f, 1f, shapePath)

        // endCenter = offsetFromEndPx = 80
        assertEquals(80f, base.capturedCenter, 0.01f)
    }

    @Test
    fun `fraction 0_5 places center at midpoint`() {
        val base = CenterCapturingTreatment()
        val treatment = AnimatableOffsetEdgeTreatment(base, offsetFromStartPx = 96f)
        treatment.offsetFromEndPx = 80f
        treatment.fraction = 0.5f

        val shapePath = ShapePath()
        treatment.getEdgePath(400f, 200f, 1f, shapePath)

        // midpoint = 304 + (80 - 304) * 0.5 = 192
        assertEquals(192f, base.capturedCenter, 0.01f)
    }

    @Test
    fun `adapts to different edge lengths`() {
        val base = CenterCapturingTreatment()
        val treatment = AnimatableOffsetEdgeTreatment(base, offsetFromStartPx = 96f)
        treatment.offsetFromEndPx = 80f
        treatment.fraction = 0f

        val shapePath = ShapePath()

        treatment.getEdgePath(400f, 200f, 1f, shapePath)
        assertEquals(304f, base.capturedCenter, 0.01f)

        treatment.getEdgePath(600f, 300f, 1f, shapePath)
        assertEquals(504f, base.capturedCenter, 0.01f)
    }

    @Test
    fun `end position is independent of edge length`() {
        val base = CenterCapturingTreatment()
        val treatment = AnimatableOffsetEdgeTreatment(base, offsetFromStartPx = 96f)
        treatment.offsetFromEndPx = 80f
        treatment.fraction = 1f

        val shapePath = ShapePath()

        treatment.getEdgePath(400f, 200f, 1f, shapePath)
        assertEquals(80f, base.capturedCenter, 0.01f)

        treatment.getEdgePath(600f, 300f, 1f, shapePath)
        assertEquals(80f, base.capturedCenter, 0.01f)
    }

    @Test
    fun `default fraction and target keep arrow at start position`() {
        val base = CenterCapturingTreatment()
        val treatment = AnimatableOffsetEdgeTreatment(base, offsetFromStartPx = 96f)

        val shapePath = ShapePath()
        treatment.getEdgePath(400f, 200f, 1f, shapePath)

        assertEquals(304f, base.capturedCenter, 0.01f)
    }
}
