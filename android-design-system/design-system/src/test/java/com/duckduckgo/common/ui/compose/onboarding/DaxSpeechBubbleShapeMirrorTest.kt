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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DaxSpeechBubbleShapeMirrorTest {

    @Test
    fun mirroredVariantKeepsTheWideStartOffsetAndReflectsTheHook() {
        val plainPlacement = tailPlacementFor(DaxOnboardingDialogTail.BottomStartWide)
        val mirroredPlacement = tailPlacementFor(DaxOnboardingDialogTail.BottomStartMirrored)
        assertEquals(plainPlacement.offsetDp, mirroredPlacement.offsetDp, 0f)
        assertEquals(plainPlacement.edgeFraction, mirroredPlacement.edgeFraction, 0f)
        assertEquals(1f, mirroredPlacement.mirrorFraction, 0f)

        val plain = render(mirror = 0f)
        val reflected = render(mirror = 1f)
        assertTailReflection(plain, reflected)
        assertNotEquals(tailPixels(plain), tailPixels(reflected))
    }

    @Test
    fun halfwayReflectionProducesASymmetricTailWithTheSameBoundaryDirection() {
        val halfway = render(mirror = 0.5f)
        assertTailReflection(halfway, halfway)
        assertNotEquals(tailPixels(render(mirror = 0f)), tailPixels(halfway))
        assertNotEquals(tailPixels(render(mirror = 1f)), tailPixels(halfway))
        assertEquals(android.graphics.Color.BLACK, halfway.getPixel(TAIL_CENTER, TAIL_TOP + 1))
    }

    @Test
    fun rtlReflectsTheWholeCardIncludingTheAlreadyMirroredHook() {
        val ltr = render(mirror = 1f)
        val rtl = render(mirror = 1f, direction = LayoutDirection.Rtl)
        for (y in 0 until ltr.height) {
            for (x in 0 until ltr.width) {
                assertReflectedPixel(ltr, rtl, x, y, ltr.width - 1 - x)
            }
        }
    }

    private fun assertTailReflection(plain: Bitmap, reflected: Bitmap) {
        for (y in TAIL_TOP until 200) {
            for (x in TAIL_START until TAIL_END) {
                assertReflectedPixel(plain, reflected, x, y, MIRROR_SUM - x)
            }
        }
    }

    private fun assertReflectedPixel(plain: Bitmap, reflected: Bitmap, x: Int, y: Int, reflectedX: Int) {
        val expected = plain.getPixel(x, y)
        val actual = reflected.getPixel(reflectedX, y)
        if (expected == actual) return
        // Native rasterization rounds opposite contour directions differently at their edge pixels.
        assertTrue("Reflected contour differs at ($x, $y)", hasAdjacentColor(reflected, reflectedX, y, expected))
        assertTrue("Original contour differs at ($x, $y)", hasAdjacentColor(plain, x, y, actual))
    }

    private fun hasAdjacentColor(bitmap: Bitmap, x: Int, y: Int, color: Int): Boolean =
        (-1..1).any { dy ->
            (-1..1).any { dx ->
                val adjacentX = x + dx
                val adjacentY = y + dy
                adjacentX in 0 until bitmap.width && adjacentY in 0 until bitmap.height &&
                    bitmap.getPixel(adjacentX, adjacentY) == color
            }
        }

    private fun tailPixels(bitmap: Bitmap): List<Int> = (TAIL_TOP until 200).flatMap { y ->
        (TAIL_START until TAIL_END).map { x -> bitmap.getPixel(x, y) }
    }

    private fun render(mirror: Float, direction: LayoutDirection = LayoutDirection.Ltr): Bitmap {
        val shape = DaxSpeechBubbleShape(
            cornerRadius = 36.dp,
            tailPosition = DaxSpeechBubbleTailPosition.FromStart(96.dp),
            mirrorFraction = mirror,
        )
        val outline = shape.createOutline(Size(400f, 200f), direction, Density(1f)) as Outline.Generic
        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawPath(outline.path.asAndroidPath(), Paint().apply { color = android.graphics.Color.BLACK })
        return bitmap
    }

    private companion object {
        // 36dp corner radius plus the 96dp start offset; the tail is 47dp wide.
        const val TAIL_CENTER = 132
        const val TAIL_START = 107
        const val TAIL_END = 157
        const val TAIL_TOP = 169
        const val MIRROR_SUM = 2 * TAIL_CENTER - 1
    }
}
