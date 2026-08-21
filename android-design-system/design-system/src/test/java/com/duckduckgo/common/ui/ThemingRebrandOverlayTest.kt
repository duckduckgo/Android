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

import android.content.res.Resources
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.Theming.Constants.FIXED_THEME_ACTIVITIES
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
class ThemingRebrandOverlayTest {

    private fun resolveDaxButtonPrimary(activity: AppCompatActivity): Int {
        val value = TypedValue()
        activity.theme.resolveAttribute(R.attr.daxButtonPrimary, value, true)
        return value.resourceId
    }

    private fun themeFor(styleResId: Int): Resources.Theme =
        ContextThemeWrapper(RuntimeEnvironment.getApplication(), styleResId).theme

    private fun resolveBoolean(
        activity: AppCompatActivity,
        attr: Int,
    ): Boolean {
        val value = TypedValue()
        activity.theme.resolveAttribute(attr, value, true)
        return value.data != 0
    }

    class FixedThemeActivity : AppCompatActivity() {
        override fun getLocalClassName(): String = FIXED_THEME_ACTIVITIES.first()
    }

    private fun fixedThemeActivity(themeResId: Int = R.style.Theme_DuckDuckGo_Dark): AppCompatActivity =
        Robolectric.buildActivity(FixedThemeActivity::class.java).get().apply {
            setTheme(themeResId)
        }

    private fun resolveColor(
        activity: AppCompatActivity,
        attr: Int,
    ): Int {
        val value = TypedValue()
        activity.theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun colorOf(colorRes: Int): Int =
        RuntimeEnvironment.getApplication().resources.getColor(colorRes, null)

    private fun themedActivity(): AppCompatActivity =
        Robolectric.buildActivity(AppCompatActivity::class.java).get()

    @Test
    fun whenFixedThemeActivityWithBrandDesignUpdateThenRebrandStyleResolves() {
        val activity = fixedThemeActivity()
        activity.applyTheme(DuckDuckGoTheme.LIGHT, applyBrandDesignUpdate = true)
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Primary, resolveDaxButtonPrimary(activity))
    }

    @Test
    fun whenFixedThemeActivityWithBrandDesignUpdateThenThemeStaysDark() {
        val activity = fixedThemeActivity()
        activity.applyTheme(DuckDuckGoTheme.LIGHT, applyBrandDesignUpdate = true)
        assertFalse(resolveBoolean(activity, android.R.attr.isLightTheme))
    }

    @Test
    fun whenFixedThemeActivityWithoutBrandDesignUpdateThenLegacyStyleResolves() {
        val activity = fixedThemeActivity()
        activity.applyTheme(DuckDuckGoTheme.LIGHT)
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_TextButton_Primary, resolveDaxButtonPrimary(activity))
    }

    @Test
    fun whenApplyThemeWithBrandDesignUpdateThenRebrandStyleResolves() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).get()
        activity.applyTheme(DuckDuckGoTheme.LIGHT, applyBrandDesignUpdate = true)
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Primary, resolveDaxButtonPrimary(activity))
    }

    @Test
    fun whenApplyThemeWithoutBrandDesignUpdateThenLegacyStyleResolves() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).get()
        activity.applyTheme(DuckDuckGoTheme.LIGHT)
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_TextButton_Primary, resolveDaxButtonPrimary(activity))
    }

    @Test
    fun whenFixedThemeActivityThemeDoesNotSupportOverlayThenOverlayIsNotApplied() {
        val activity = fixedThemeActivity(R.style.Theme_AppCompat_Transparent_NoActionBar)
        activity.applyTheme(DuckDuckGoTheme.LIGHT, applyBrandDesignUpdate = true)
        assertNotEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Primary, resolveDaxButtonPrimary(activity))
    }

    @Test
    fun whenThemeIsDuckDuckGoDarkThenRebrandOverlayIsSupported() {
        assertTrue(themeFor(R.style.Theme_DuckDuckGo_Dark).supportsRebrandOverlay())
    }

    @Test
    fun whenThemeIsDuckDuckGoLightThenRebrandOverlayIsSupported() {
        assertTrue(themeFor(R.style.Theme_DuckDuckGo_Light).supportsRebrandOverlay())
    }

    @Test
    fun whenThemeIsAppCompatTransparentThenRebrandOverlayIsNotSupported() {
        assertFalse(themeFor(R.style.Theme_AppCompat_Transparent_NoActionBar).supportsRebrandOverlay())
    }

    @Test
    fun whenLightThemeWithBrandDesignUpdateThenAccentBlueIsPondwater60() {
        val activity = themedActivity()
        activity.applyTheme(DuckDuckGoTheme.LIGHT, applyBrandDesignUpdate = true)
        assertEquals(colorOf(R.color.pondwater60), resolveColor(activity, R.attr.daxColorAccentBlue))
    }

    @Test
    fun whenDarkThemeWithBrandDesignUpdateThenAccentBlueIsPondwater40() {
        val activity = themedActivity()
        activity.applyTheme(DuckDuckGoTheme.DARK, applyBrandDesignUpdate = true)
        assertEquals(colorOf(R.color.pondwater40), resolveColor(activity, R.attr.daxColorAccentBlue))
    }

    @Test
    fun whenLightThemeWithoutBrandDesignUpdateThenAccentBlueIsUnchanged() {
        val activity = themedActivity()
        activity.applyTheme(DuckDuckGoTheme.LIGHT)
        assertEquals(colorOf(R.color.blue50), resolveColor(activity, R.attr.daxColorAccentBlue))
    }

    @Test
    fun whenDarkThemeWithoutBrandDesignUpdateThenAccentBlueIsUnchanged() {
        val activity = themedActivity()
        activity.applyTheme(DuckDuckGoTheme.DARK)
        assertEquals(colorOf(R.color.blue30), resolveColor(activity, R.attr.daxColorAccentBlue))
    }

    @Test
    fun whenBrandDesignUpdateThenSwitchTrackFollowsAccentBlue() {
        val activity = themedActivity()
        activity.applyTheme(DuckDuckGoTheme.LIGHT, applyBrandDesignUpdate = true)
        assertEquals(
            resolveColor(activity, R.attr.daxColorAccentBlue),
            resolveColor(activity, R.attr.daxColorSwitchTrackOn),
        )
    }
}
