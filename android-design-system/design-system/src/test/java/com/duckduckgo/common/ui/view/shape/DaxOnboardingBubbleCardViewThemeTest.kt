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

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class DaxOnboardingBubbleCardViewThemeTest {

    private val app: Context get() = RuntimeEnvironment.getApplication()
    private var lightSurface = 0
    private var darkSurface = 0

    @Before
    fun setup() {
        lightSurface = ContextThemeWrapper(app, R.style.Theme_DuckDuckGo_Light_Onboarding).getColorFromAttr(R.attr.onboardingSurfaceTertiary)
        darkSurface = ContextThemeWrapper(app, R.style.Theme_DuckDuckGo_Dark_Onboarding).getColorFromAttr(R.attr.onboardingSurfaceTertiary)
        assertNotEquals(lightSurface, darkSurface)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun whenAppThemeIsDarkAndSystemIsLightThenCardIsDark() {
        assertEquals(darkSurface, cardColor(R.style.Theme_DuckDuckGo_Dark))
    }

    @Test
    @Config(qualifiers = "night")
    fun whenAppThemeIsLightAndSystemIsDarkThenCardIsLight() {
        assertEquals(lightSurface, cardColor(R.style.Theme_DuckDuckGo_Light))
    }

    @Test
    @Config(qualifiers = "night")
    fun whenContextIsAlreadyAnOnboardingThemeThenCardUsesIt() {
        assertEquals(lightSurface, cardColor(R.style.Theme_DuckDuckGo_Light_Onboarding))
    }

    private fun cardColor(themeRes: Int): Int =
        DaxOnboardingBubbleCardView(ContextThemeWrapper(app, themeRes)).cardBackgroundColor.defaultColor
}
