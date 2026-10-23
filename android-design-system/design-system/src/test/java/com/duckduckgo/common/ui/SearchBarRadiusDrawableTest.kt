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
import android.graphics.drawable.LayerDrawable
import android.view.ContextThemeWrapper
import androidx.appcompat.content.res.AppCompatResources
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class SearchBarRadiusDrawableTest {

    @Test
    fun whenRadiusOverlayIsAbsentThenSearchBarDrawablesUse8DpInLightAndDarkThemes() {
        assertSearchBarCornerRadii(overlayEnabled = false, expectedDimen = R.dimen.smallShapeCornerRadius)
    }

    @Test
    @Config(qualifiers = "xhdpi")
    fun whenRadiusOverlayIsAppliedThenSearchBarDrawablesUse48DpInLightAndDarkThemes() {
        assertSearchBarCornerRadii(overlayEnabled = true, expectedDimen = R.dimen.rebrandInputRadius)
    }

    private fun assertSearchBarCornerRadii(overlayEnabled: Boolean, expectedDimen: Int) {
        listOf(R.style.Theme_DuckDuckGo_Light, R.style.Theme_DuckDuckGo_Dark).forEach { themeResId ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), themeResId)
            if (overlayEnabled) context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_Radius, true)
            val expectedRadius = context.resources.getDimension(expectedDimen)

            val background = checkNotNull(AppCompatResources.getDrawable(context, R.drawable.omnibar_field_background)) as GradientDrawable
            assertEquals(expectedRadius, background.cornerRadius, 0f)

            val focused = checkNotNull(AppCompatResources.getDrawable(context, R.drawable.omnibar_field_background_focused)) as LayerDrawable
            for (index in 0 until focused.numberOfLayers) {
                assertEquals(expectedRadius, (focused.getDrawable(index) as GradientDrawable).cornerRadius, 0f)
            }
        }
    }
}
