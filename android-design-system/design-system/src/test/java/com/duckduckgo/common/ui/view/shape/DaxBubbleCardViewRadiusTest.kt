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

import android.graphics.RectF
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class DaxBubbleCardViewRadiusTest {

    @Test
    fun whenRadiusOverlayIsAbsentThenBubbleUsesLegacy12DpRadius() {
        assertBubbleRadius(
            overlayEnabled = false,
            expectedDimen = R.dimen.mediumShapeCornerRadius,
        )
    }

    @Test
    @Config(qualifiers = "xhdpi")
    fun whenRadiusOverlayIsAppliedThenBubbleUses28DpRadius() {
        assertBubbleRadius(
            overlayEnabled = true,
            expectedDimen = R.dimen.rebrandContainerRadius,
        )
    }

    private fun assertBubbleRadius(
        overlayEnabled: Boolean,
        expectedDimen: Int,
    ) {
        listOf(
            R.style.Theme_DuckDuckGo_Light,
            R.style.Theme_DuckDuckGo_Dark,
        ).forEach { themeRes ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), themeRes)
            if (overlayEnabled) {
                context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_Radius, true)
            }
            val shape = DaxBubbleCardView(context).shapeAppearanceModel
            val expected = context.resources.getDimension(expectedDimen)
            val bounds = RectF()

            assertEquals(expected, shape.topLeftCornerSize.getCornerSize(bounds), 0f)
            assertEquals(expected, shape.topRightCornerSize.getCornerSize(bounds), 0f)
            assertEquals(expected, shape.bottomLeftCornerSize.getCornerSize(bounds), 0f)
            assertEquals(expected, shape.bottomRightCornerSize.getCornerSize(bounds), 0f)
        }
    }
}
