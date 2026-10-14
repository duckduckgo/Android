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

package com.duckduckgo.common.ui

import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.view.DaxYellowPill
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class YellowPillRadiusDrawableTest {

    @Test
    fun whenRadiusOverlayIsAbsentThenYellowPillUses2DpInLightAndDarkThemes() {
        assertPillCornerRadius(
            overlayEnabled = false,
            expectedDimen = R.dimen.daxYellowPillCornerRadius,
        )
    }

    @Test
    @Config(qualifiers = "xhdpi")
    fun whenRadiusOverlayIsAppliedThenYellowPillUsesPillRadiusInLightAndDarkThemes() {
        assertPillCornerRadius(
            overlayEnabled = true,
            expectedDimen = R.dimen.pillShapeCornerRadius,
        )
    }

    private fun assertPillCornerRadius(
        overlayEnabled: Boolean,
        expectedDimen: Int,
    ) {
        listOf(
            R.style.Theme_DuckDuckGo_Light,
            R.style.Theme_DuckDuckGo_Dark,
        ).forEach { themeResId ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), themeResId)
            if (overlayEnabled) {
                context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_Radius, true)
            }
            val expectedRadius = context.resources.getDimension(expectedDimen)
            val drawable = DaxYellowPill(context).background as GradientDrawable

            assertEquals(expectedRadius, drawable.getCornerRadius(), 0f)
        }
    }
}
