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

import android.view.ContextThemeWrapper
import androidx.annotation.StyleRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.google.android.material.R as MaterialR

@RunWith(AndroidJUnit4::class)
class DialogRadiusStyleTest {

    @Test
    fun whenRadiusOverlayIsAbsentThenSharedDialogCornersUse12Dp() {
        listOf(
            R.style.Theme_DuckDuckGo_Light,
            R.style.Theme_DuckDuckGo_Dark,
        ).forEach { themeRes ->
            assertEquals(
                RuntimeEnvironment.getApplication().resources.getDimension(R.dimen.dialogBorderRadius),
                resolvedDialogCorner(themeRes, overlayEnabled = false),
                0f,
            )
        }
    }

    @Test
    @Config(qualifiers = "xhdpi")
    fun whenRadiusOverlayIsAppliedThenSharedDialogCornersUse28Dp() {
        listOf(
            R.style.Theme_DuckDuckGo_Light,
            R.style.Theme_DuckDuckGo_Dark,
        ).forEach { themeRes ->
            assertEquals(
                RuntimeEnvironment.getApplication().resources.getDimension(R.dimen.rebrandContainerRadius),
                resolvedDialogCorner(themeRes, overlayEnabled = true),
                0f,
            )
        }
    }

    @Test
    fun whenRadiusOverlayIsAbsentThenLottieLandscapeOutlineUses16Dp() {
        listOf(
            R.style.Theme_DuckDuckGo_Light,
            R.style.Theme_DuckDuckGo_Dark,
        ).forEach { themeRes ->
            assertEquals(
                RuntimeEnvironment.getApplication().resources.getDimension(R.dimen.largeShapeCornerRadius),
                resolvedDaxDialogOutlineCorner(themeRes, overlayEnabled = false),
                0f,
            )
        }
    }

    @Test
    @Config(qualifiers = "xhdpi")
    fun whenRadiusOverlayIsAppliedThenLottieLandscapeOutlineUses28Dp() {
        listOf(
            R.style.Theme_DuckDuckGo_Light,
            R.style.Theme_DuckDuckGo_Dark,
        ).forEach { themeRes ->
            assertEquals(
                RuntimeEnvironment.getApplication().resources.getDimension(R.dimen.rebrandContainerRadius),
                resolvedDaxDialogOutlineCorner(themeRes, overlayEnabled = true),
                0f,
            )
        }
    }

    private fun resolvedDialogCorner(
        @StyleRes themeRes: Int,
        overlayEnabled: Boolean,
    ): Float {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), themeRes)
        if (overlayEnabled) {
            context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_Radius, true)
        }
        val values = context.obtainStyledAttributes(
            R.style.Widget_DuckDuckGo_DialogCorners,
            intArrayOf(MaterialR.attr.cornerSize),
        )
        return try {
            values.getDimension(0, -1f)
        } finally {
            values.recycle()
        }
    }

    private fun resolvedDaxDialogOutlineCorner(
        @StyleRes themeRes: Int,
        overlayEnabled: Boolean,
    ): Float {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), themeRes)
        if (overlayEnabled) {
            context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_Radius, true)
        }
        val values = context.obtainStyledAttributes(
            R.style.Widget_DuckDuckGo_DaxDialog_Outline,
            intArrayOf(MaterialR.attr.cardCornerRadius),
        )
        return try {
            values.getDimension(0, -1f)
        } finally {
            values.recycle()
        }
    }
}
