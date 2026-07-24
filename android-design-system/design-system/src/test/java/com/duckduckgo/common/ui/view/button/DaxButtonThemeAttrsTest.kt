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

package com.duckduckgo.common.ui.view.button

import android.content.Context
import android.util.AttributeSet
import androidx.test.core.app.ApplicationProvider
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaxButtonThemeAttrsTest {

    private fun themedContext(overlay: Boolean): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.setTheme(R.style.Theme_DuckDuckGo_Light)
        if (overlay) {
            context.theme.applyStyle(R.style.ThemeOverlay_DaxButton_Rebrand, true)
        }
        return context
    }

    private fun buttonAttrs(large: Boolean, explicitTextAppearance: Boolean = false): AttributeSet {
        val builder = Robolectric.buildAttributeSet()
        if (large) builder.addAttribute(R.attr.daxButtonSize, "large")
        if (explicitTextAppearance) {
            builder.addAttribute(android.R.attr.textAppearance, "@style/Typography.DuckDuckGo.Caption")
        }
        return builder.build()
    }

    @Test
    fun whenBaseThemeSmallThenLegacyMetricsApply() {
        val button = DaxButtonPrimary(themedContext(overlay = false), buttonAttrs(large = false))
        assertEquals(48, button.minHeight)
        assertEquals(16, button.paddingLeft)
        assertEquals(8, button.paddingTop)
    }

    @Test
    fun whenBaseThemeLargeThenLegacyMetricsAndTextSizeApply() {
        val button = DaxButtonPrimary(themedContext(overlay = false), buttonAttrs(large = true))
        assertEquals(60, button.minHeight)
        assertEquals(24, button.paddingLeft)
        assertEquals(14, button.paddingTop)
        assertEquals(15f, button.textSize)
    }

    @Test
    fun whenOverlaySmallThenRebrandMetricsAndInsetsApply() {
        val button = DaxButtonPrimary(themedContext(overlay = true), buttonAttrs(large = false))
        assertEquals(48, button.minHeight)
        assertEquals(16, button.paddingLeft)
        assertEquals(10, button.paddingTop)
        assertEquals(4, button.insetTop)
        assertEquals(4, button.insetBottom)
    }

    @Test
    fun whenOverlayLargeThenRebrandMetricsAndLargeTextAppearanceApply() {
        val button = DaxButtonPrimary(themedContext(overlay = true), buttonAttrs(large = true))
        assertEquals(68, button.minHeight)
        assertEquals(24, button.paddingLeft)
        assertEquals(18, button.paddingTop)
        assertEquals(6, button.insetTop)
        assertEquals(6, button.insetBottom)
        assertEquals(16f, button.textSize)
    }

    @Test
    fun whenLayoutSetsTextAppearanceThenLargeTextAppearanceIsNotApplied() {
        val button = DaxButtonPrimary(themedContext(overlay = true), buttonAttrs(large = true, explicitTextAppearance = true))
        assertEquals(12f, button.textSize)
    }
}
