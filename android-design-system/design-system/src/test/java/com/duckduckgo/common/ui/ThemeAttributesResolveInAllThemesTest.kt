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

import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

/**
 * While the new theme toggle exists, every `daxColor*` attribute must be defined in both the old
 * and the new themes so that turning the toggle off never leaves new work without a value.
 */
@RunWith(AndroidJUnit4::class)
class ThemeAttributesResolveInAllThemesTest {

    private val themes = mapOf(
        "Theme.DuckDuckGo.Light" to R.style.Theme_DuckDuckGo_Light,
        "Theme.DuckDuckGo.Dark" to R.style.Theme_DuckDuckGo_Dark,
        "Theme.DuckDuckGo.New.Light" to R.style.Theme_DuckDuckGo_New_Light,
        "Theme.DuckDuckGo.New.Dark" to R.style.Theme_DuckDuckGo_New_Dark,
    )

    @Test
    fun everyDaxColorAttributeResolvesInEveryTheme() {
        val attrs = R.attr::class.java.fields.filter { it.name.startsWith("daxColor") }
        assertTrue("No daxColor attributes found", attrs.isNotEmpty())

        val gaps = themes.flatMap { (themeName, styleRes) ->
            val theme = ContextThemeWrapper(RuntimeEnvironment.getApplication(), styleRes).theme
            attrs.filterNot { theme.resolveAttribute(it.getInt(null), TypedValue(), true) }
                .map { "$themeName: ${it.name}" }
        }

        assertTrue("Attributes missing from a theme:\n${gaps.joinToString("\n")}", gaps.isEmpty())
    }
}
