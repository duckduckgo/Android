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

/**
 * A mutable [EdgeTreatment] wrapper that interpolates the arrow center between two positions.
 *
 * The bottom edge in Material is drawn right-to-left (clockwise path), so edge-local x=0
 * is the visual right side of the view.
 *
 * @param base the underlying edge treatment (e.g. [DaxBubbleBottomEdgeTreatment])
 * @param offsetFromStartPx the initial arrow position as a visual offset from the left edge.
 *   Edge-local center = `length - offsetFromStartPx`.
 */
class AnimatableOffsetEdgeTreatment(
    private val base: EdgeTreatment,
    private val offsetFromStartPx: Float,
) : EdgeTreatment() {

    /**
     * Target arrow position as a visual offset from the right edge.
     * Edge-local center = `offsetFromEndPx` (since edge-local 0 is the visual right).
     */
    var offsetFromEndPx: Float = 0f

    /**
     * Animation fraction: 0 = arrow at [offsetFromStartPx], 1 = arrow at [offsetFromEndPx].
     */
    var fraction: Float = 0f

    override fun getEdgePath(
        length: Float,
        center: Float,
        interpolation: Float,
        shapePath: ShapePath,
    ) {
        val startCenter = length - offsetFromStartPx
        val endCenter = offsetFromEndPx
        val animatedCenter = startCenter + (endCenter - startCenter) * fraction
        base.getEdgePath(length, animatedCenter, interpolation, shapePath)
    }
}
