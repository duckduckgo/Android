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

package com.duckduckgo.common.ui.compose.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class DaxSpeechBubbleShapeTest {

    @Test
    fun logicalPlacementInterpolatesStartAndEndAcrossTheCard() {
        assertEquals(80f, logicalTailCenter(widthPx = 400f, offsetPx = 80f, edgeFraction = 0f), 0.001f)
        assertEquals(320f, logicalTailCenter(widthPx = 400f, offsetPx = 80f, edgeFraction = 1f), 0.001f)
        assertEquals(200f, logicalTailCenter(widthPx = 400f, offsetPx = 96f, edgeFraction = 0.5f), 0.001f)
    }

    @Test
    fun logicalPlacementMirrorsAcrossRtlWithoutChangingLogicalTargets() {
        val start = logicalTailCenter(widthPx = 400f, offsetPx = 80f, edgeFraction = 0f)
        val end = logicalTailCenter(widthPx = 400f, offsetPx = 80f, edgeFraction = 1f)

        assertEquals(320f, 400f - start, 0.001f)
        assertEquals(80f, 400f - end, 0.001f)
    }
}
