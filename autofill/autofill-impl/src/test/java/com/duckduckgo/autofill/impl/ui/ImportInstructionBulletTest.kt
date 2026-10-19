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

package com.duckduckgo.autofill.impl.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.appcompat.content.res.AppCompatResources
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class ImportInstructionBulletTest {

    // The system night qualifier is deliberately the opposite of the theme under test:
    // the colour must follow the app theme, not the system setting.

    @Test
    @Config(qualifiers = "night")
    fun whenLightThemeIsUsedThenAccentGlowSecondaryAndBulletUseLightColor() {
        assertBulletColor(CommonR.style.Theme_DuckDuckGo_Light, Color.parseColor("#1F1074CC"))
    }

    @Test
    @Config(qualifiers = "notnight")
    fun whenDarkThemeIsUsedThenAccentGlowSecondaryAndBulletUseDarkColor() {
        assertBulletColor(CommonR.style.Theme_DuckDuckGo_Dark, Color.parseColor("#1F75B6EB"))
    }

    private fun assertBulletColor(themeResId: Int, expectedColor: Int) {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), themeResId)
        val value = TypedValue()
        assertTrue(context.theme.resolveAttribute(CommonR.attr.daxColorAccentGlowSecondary, value, true))
        assertEquals(expectedColor, value.data)
        val drawable = checkNotNull(AppCompatResources.getDrawable(context, R.drawable.autofill_import_instruction_bullet)) as GradientDrawable
        assertEquals(expectedColor, drawable.color?.defaultColor)
    }
}
