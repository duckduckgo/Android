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
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.google.android.material.R as MaterialR

@RunWith(RobolectricTestRunner::class)
class RebrandButtonsOverlayTest {

    private fun themedContext(overlay: Boolean): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.setTheme(R.style.Theme_DuckDuckGo_Light)
        if (overlay) {
            context.theme.applyStyle(R.style.ThemeOverlay_DaxButton_Rebrand, true)
        }
        return context
    }

    private fun Context.resolveStyleAttr(attr: Int): Int {
        val value = TypedValue()
        assertTrue(theme.resolveAttribute(attr, value, true))
        return value.resourceId
    }

    @Test
    fun whenOverlayAppliedThenDaxButtonStyleAttrsResolveToRebrandStyles() {
        val context = themedContext(overlay = true)
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Primary, context.resolveStyleAttr(R.attr.daxButtonPrimary))
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_SecondaryFill, context.resolveStyleAttr(R.attr.daxButtonSecondary))
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Ghost, context.resolveStyleAttr(R.attr.daxButtonGhost))
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Ghost, context.resolveStyleAttr(R.attr.daxButtonGhostAlt))
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_DestructivePrimary, context.resolveStyleAttr(R.attr.daxButtonDestructive))
        assertEquals(
            R.style.Widget_DuckDuckGo_DaxButton_Rebrand_DestructiveSecondary,
            context.resolveStyleAttr(R.attr.daxButtonDestructiveSecondary),
        )
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_DestructiveGhost, context.resolveStyleAttr(R.attr.daxButtonGhostDestructive))
        assertEquals(R.style.Typography_DuckDuckGo_Rebrand_Button, context.resolveStyleAttr(MaterialR.attr.textAppearanceButton))
        assertEquals(R.style.Typography_DuckDuckGo_Rebrand_ButtonLarge, context.resolveStyleAttr(R.attr.textAppearanceButtonLarge))
    }

    @Test
    fun whenOverlayNotAppliedThenDaxButtonStyleAttrsResolveToLegacyStyles() {
        val context = themedContext(overlay = false)
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_TextButton_Primary, context.resolveStyleAttr(R.attr.daxButtonPrimary))
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Secondary, context.resolveStyleAttr(R.attr.daxButtonSecondary))
        assertEquals(R.style.Typography_DuckDuckGo_Button, context.resolveStyleAttr(MaterialR.attr.textAppearanceButton))
        assertEquals(R.style.Typography_DuckDuckGo_Button, context.resolveStyleAttr(R.attr.textAppearanceButtonLarge))
    }

    @Test
    fun whenOverlayNotAppliedThenVerticalInsetAttrsAreUndefined() {
        val context = themedContext(overlay = false)
        val value = TypedValue()
        assertFalse(context.theme.resolveAttribute(R.attr.daxButtonSmallVerticalInset, value, true))
        assertFalse(context.theme.resolveAttribute(R.attr.daxButtonLargeVerticalInset, value, true))
    }

    @Test
    fun whenBaseThemeThenBrandAttrsResolveWithoutOverlay() {
        val context = themedContext(overlay = false)
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Brand, context.resolveStyleAttr(R.attr.daxButtonBrand))
        val value = TypedValue()
        assertTrue(context.theme.resolveAttribute(R.attr.daxColorButtonBrandContainer, value, true))
    }
}
