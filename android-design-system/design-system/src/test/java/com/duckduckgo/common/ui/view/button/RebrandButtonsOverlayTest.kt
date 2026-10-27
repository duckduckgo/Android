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
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.appcompat.content.res.AppCompatResources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.google.android.material.R as MaterialR

@RunWith(AndroidJUnit4::class)
class RebrandButtonsOverlayTest {

    private fun themedContext(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.setTheme(R.style.Theme_DuckDuckGo_Light)
        return context
    }

    private fun Context.resolveStyleAttr(attr: Int): Int {
        val value = TypedValue()
        assertTrue(theme.resolveAttribute(attr, value, true))
        return value.resourceId
    }

    @Test
    fun whenBaseThemeThenDaxButtonStyleAttrsResolveToRebrandStyles() {
        val context = themedContext()
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
    fun whenBaseThemeThenBrandAttrsResolve() {
        val context = themedContext()
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Brand, context.resolveStyleAttr(R.attr.daxButtonBrand))
        val value = TypedValue()
        assertTrue(context.theme.resolveAttribute(R.attr.daxColorButtonBrandContainer, value, true))
    }

    @Test
    fun whenOnboardingThemeWrapsBaseThemeThenButtonStyleAttrsStayRebrand() {
        val context = ContextThemeWrapper(themedContext(), R.style.ThemeOverlay_DuckDuckGo_Onboarding)
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_SecondaryFill, context.resolveStyleAttr(R.attr.daxButtonSecondary))
        assertEquals(R.style.Typography_DuckDuckGo_Rebrand_Button, context.resolveStyleAttr(MaterialR.attr.textAppearanceButton))
    }

    private fun Context.colorStateList(colorRes: Int): ColorStateList =
        AppCompatResources.getColorStateList(this, colorRes)

    private fun ColorStateList.pressedColor(): Int =
        getColorForState(intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed), defaultColor)

    private fun Context.resolveRippleColorRes(styleRes: Int): Int {
        val typedArray = obtainStyledAttributes(styleRes, intArrayOf(MaterialR.attr.rippleColor))
        val resourceId = typedArray.getResourceId(0, 0)
        typedArray.recycle()
        return resourceId
    }

    @Test
    fun whenRebrandContainerSelectorsThenPressedStateMatchesRestingColor() {
        val context = themedContext()
        val containers = listOf(
            R.color.button_brand_container_selector,
            R.color.rebrand_button_primary_container_selector,
            R.color.rebrand_button_secondary_fill_container_selector,
            R.color.rebrand_button_ghost_container_selector,
            R.color.rebrand_button_destructive_primary_container_selector,
            R.color.rebrand_button_destructive_ghost_container_selector,
        )
        containers.forEach { colorRes ->
            val stateList = context.colorStateList(colorRes)
            assertEquals(stateList.defaultColor, stateList.pressedColor())
        }
    }

    @Test
    fun whenRebrandTextSelectorsThenPressedStateMatchesRestingColor() {
        val context = themedContext()
        val textSelectors = listOf(
            R.color.rebrand_button_ghost_text_selector,
            R.color.rebrand_button_destructive_secondary_text_selector,
            R.color.rebrand_button_destructive_ghost_text_selector,
        )
        textSelectors.forEach { colorRes ->
            val stateList = context.colorStateList(colorRes)
            assertEquals(stateList.defaultColor, stateList.pressedColor())
        }
    }

    @Test
    fun whenRebrandStylesThenRippleColorResolvesToARippleSelector() {
        val context = themedContext()
        assertEquals(
            R.color.rebrand_button_primary_ripple_selector,
            context.resolveRippleColorRes(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Primary),
        )
        assertEquals(
            R.color.rebrand_button_secondary_fill_ripple_selector,
            context.resolveRippleColorRes(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_SecondaryFill),
        )
        assertEquals(
            R.color.rebrand_button_ghost_ripple_selector,
            context.resolveRippleColorRes(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Ghost),
        )
        assertEquals(
            R.color.rebrand_button_destructive_primary_ripple_selector,
            context.resolveRippleColorRes(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_DestructivePrimary),
        )
        assertEquals(
            R.color.rebrand_button_secondary_fill_ripple_selector,
            context.resolveRippleColorRes(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_DestructiveSecondary),
        )
        assertEquals(
            R.color.rebrand_button_destructive_ghost_ripple_selector,
            context.resolveRippleColorRes(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_DestructiveGhost),
        )
        assertEquals(
            R.color.button_brand_ripple_selector,
            context.resolveRippleColorRes(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Brand),
        )
    }
}
