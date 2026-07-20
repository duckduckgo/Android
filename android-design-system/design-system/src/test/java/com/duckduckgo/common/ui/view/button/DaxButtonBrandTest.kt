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
class DaxButtonBrandTest {

    private fun themedContext(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.setTheme(R.style.Theme_DuckDuckGo_Light)
        return context
    }

    private fun brandAttrs(
        large: Boolean = false,
        duckSans: Boolean = false,
    ): AttributeSet {
        val builder = Robolectric.buildAttributeSet()
        if (large) builder.addAttribute(R.attr.daxButtonSize, "large")
        if (duckSans) builder.addAttribute(R.attr.daxButtonBrandTypography, "duckSans")
        return builder.build()
    }

    @Test
    fun whenSmallWithoutOverlayThenRebrandMetricsAndTypographyApply() {
        val button = DaxButtonBrand(themedContext(), brandAttrs())
        assertEquals(48, button.minHeight)
        assertEquals(4, button.insetTop)
        assertEquals(10, button.paddingTop)
        assertEquals(15f, button.textSize)
    }

    @Test
    fun whenLargeWithoutOverlayThenRebrandLargeMetricsAndTypographyApply() {
        val button = DaxButtonBrand(themedContext(), brandAttrs(large = true))
        assertEquals(68, button.minHeight)
        assertEquals(6, button.insetTop)
        assertEquals(6, button.insetBottom)
        assertEquals(18, button.paddingTop)
        assertEquals(16f, button.textSize)
    }

    @Test
    fun whenDuckSansTypographyThenDuckSansAppearanceApplies() {
        val button = DaxButtonBrand(themedContext(), brandAttrs(duckSans = true))
        assertEquals(18f, button.textSize)
    }
}
