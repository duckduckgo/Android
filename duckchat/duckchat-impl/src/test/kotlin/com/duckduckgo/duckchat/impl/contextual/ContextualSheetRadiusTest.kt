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

package com.duckduckgo.duckchat.impl.contextual

import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import androidx.annotation.DimenRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.impl.R
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class ContextualSheetRadiusTest {

    @Test
    fun whenRadiusOverlayIsAbsentThenContextualSheetKeeps12DpCorners() {
        assertTopCorners(
            overlayEnabled = false,
            expectedDimen = CommonR.dimen.dialogBorderRadius,
        )
    }

    @Test
    @Config(qualifiers = "xhdpi")
    fun whenRadiusOverlayIsAppliedThenContextualSheetUses28DpCorners() {
        assertTopCorners(
            overlayEnabled = true,
            expectedDimen = CommonR.dimen.rebrandContainerRadius,
        )
    }

    private fun assertTopCorners(
        overlayEnabled: Boolean,
        @DimenRes expectedDimen: Int,
    ) {
        listOf(
            CommonR.style.Theme_DuckDuckGo_Light,
            CommonR.style.Theme_DuckDuckGo_Dark,
        ).forEach { themeRes ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), themeRes)
            if (overlayEnabled) {
                context.theme.applyStyle(CommonR.style.ThemeOverlay_Rebrand_Radius, true)
            }
            val drawable = checkNotNull(AppCompatResources.getDrawable(context, R.drawable.contextual_sheet_background)) as GradientDrawable
            val expected = context.resources.getDimension(expectedDimen)
            assertArrayEquals(
                floatArrayOf(expected, expected, expected, expected, 0f, 0f, 0f, 0f),
                checkNotNull(drawable.cornerRadii),
                0f,
            )
        }
    }
}
