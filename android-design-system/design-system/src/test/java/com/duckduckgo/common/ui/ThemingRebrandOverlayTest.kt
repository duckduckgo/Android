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

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.RectF
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.Theming.Constants.FIXED_THEME_ACTIVITIES
import com.duckduckgo.common.ui.store.AppBrandDesignUpdateToggles
import com.duckduckgo.common.ui.store.ThemingDataStore
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.mobile.android.R
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.snackbar.Snackbar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val EXTRA_THEME = "theme"
private const val EXTRA_THEME_RES_ID = "themeResId"
private const val EXTRA_FIXED_LOCAL_CLASS_NAME = "fixedLocalClassName"
private const val EXTRA_FIRE_THEME = "fireTheme"

private class TestThemingDataStore(override var theme: DuckDuckGoTheme = DuckDuckGoTheme.LIGHT) : ThemingDataStore {
    override fun isCurrentlySelected(theme: DuckDuckGoTheme): Boolean = this.theme == theme
}

@RunWith(AndroidJUnit4::class)
class ThemingRebrandOverlayTest {

    private fun resolveDaxButtonPrimary(activity: AppCompatActivity): Int {
        val value = TypedValue()
        activity.theme.resolveAttribute(R.attr.daxButtonPrimary, value, true)
        return value.resourceId
    }

    private fun resolveBoolean(
        activity: Context,
        attr: Int,
    ): Boolean {
        val value = TypedValue()
        activity.theme.resolveAttribute(attr, value, true)
        return value.data != 0
    }

    class ThemedActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            val theme = DuckDuckGoTheme.valueOf(intent.getStringExtra(EXTRA_THEME) ?: DuckDuckGoTheme.LIGHT.name)
            setTheme(intent.getIntExtra(EXTRA_THEME_RES_ID, R.style.Theme_DuckDuckGo_Dark))
            applyTheme(theme = theme)
            super.onCreate(savedInstanceState)
        }

        override fun getLocalClassName(): String =
            intent.getStringExtra(EXTRA_FIXED_LOCAL_CLASS_NAME) ?: super.getLocalClassName()
    }

    class FireAwareActivity : DuckDuckGoActivity() {
        override val applyFireTheme: Boolean
            get() = intent.getBooleanExtra(EXTRA_FIRE_THEME, false)

        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_DuckDuckGo_Light)
            themingDataStore = TestThemingDataStore(
                DuckDuckGoTheme.valueOf(intent.getStringExtra(EXTRA_THEME) ?: DuckDuckGoTheme.LIGHT.name),
            )
            appBrandDesignUpdateToggles = mock<AppBrandDesignUpdateToggles>().also {
                whenever(it.newTheme()).thenReturn(mock<Toggle>())
            }
            onCreate(savedInstanceState, injectDependencies = false)
        }
    }

    private fun fixedThemeActivity(
        themeResId: Int = R.style.Theme_DuckDuckGo_Dark,
        fixedLocalClassName: String = FIXED_THEME_ACTIVITIES[1],
    ): AppCompatActivity = buildThemedActivity(
        theme = DuckDuckGoTheme.LIGHT,
        themeResId = themeResId,
        fixedLocalClassName = fixedLocalClassName,
    )

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

    private fun themedActivity(theme: DuckDuckGoTheme = DuckDuckGoTheme.LIGHT): AppCompatActivity = buildThemedActivity(theme = theme)

    private fun buildThemedActivity(
        theme: DuckDuckGoTheme,
        themeResId: Int = R.style.Theme_DuckDuckGo_Dark,
        fixedLocalClassName: String? = null,
    ): AppCompatActivity {
        val intent = Intent().apply {
            putExtra(EXTRA_THEME, theme.name)
            putExtra(EXTRA_THEME_RES_ID, themeResId)
            if (fixedLocalClassName != null) putExtra(EXTRA_FIXED_LOCAL_CLASS_NAME, fixedLocalClassName)
        }
        return Robolectric.buildActivity(ThemedActivity::class.java, intent).setup().get()
    }

    private fun fireAwareActivity(
        theme: DuckDuckGoTheme = DuckDuckGoTheme.LIGHT,
        fireTheme: Boolean = false,
    ): AppCompatActivity {
        val intent = Intent()
            .putExtra(EXTRA_THEME, theme.name)
            .putExtra(EXTRA_FIRE_THEME, fireTheme)
        return Robolectric.buildActivity(FireAwareActivity::class.java, intent).setup().get()
    }

    private fun resolveDimension(
        activity: Context,
        attr: Int,
    ): Float {
        val value = TypedValue()
        assertTrue(activity.theme.resolveAttribute(attr, value, true))
        return if (value.resourceId != 0) {
            activity.resources.getDimension(value.resourceId)
        } else {
            TypedValue.complexToDimension(value.data, activity.resources.displayMetrics)
        }
    }

    private fun resolveResourceId(
        activity: Context,
        attr: Int,
    ): Int {
        val value = TypedValue()
        assertTrue(activity.theme.resolveAttribute(attr, value, true))
        return value.resourceId
    }

    @Test
    fun whenFixedThemeActivityThenRebrandStyleResolves() {
        val activity = fixedThemeActivity()
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Primary, resolveDaxButtonPrimary(activity))
    }

    @Test
    fun whenFixedThemeActivityThenThemeStaysDark() {
        val activity = fixedThemeActivity()
        assertFalse(resolveBoolean(activity, android.R.attr.isLightTheme))
    }

    @Test
    fun whenApplyThemeThenRebrandStyleResolves() {
        val activity = themedActivity()
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Primary, resolveDaxButtonPrimary(activity))
    }

    @Test
    fun whenLightAndDarkThemesThenRadiusValuesResolveToRebrandValues() {
        listOf(DuckDuckGoTheme.LIGHT, DuckDuckGoTheme.DARK).forEach { theme ->
            val activity = themedActivity(theme)

            assertEquals(28f, resolveDimension(activity, R.attr.daxContainerRadius), 0f)
            assertEquals(28f, resolveDimension(activity, R.attr.daxDialogRadius), 0f)
            assertEquals(28f, resolveDimension(activity, R.attr.daxSheetRadius), 0f)
            assertEquals(28f, resolveDimension(activity, R.attr.daxLargeSheetRadius), 0f)
            assertEquals(16f, resolveDimension(activity, R.attr.daxMenuRadius), 0f)
            assertEquals(48f, resolveDimension(activity, R.attr.daxSearchBarRadius), 0f)
            assertEquals(1000f, resolveDimension(activity, R.attr.daxChipRadius), 0f)
            assertEquals(1000f, resolveDimension(activity, R.attr.daxDuckAiPromptRadius), 0f)
            assertEquals(28f, resolveDimension(activity, R.attr.daxWhatsNewCardRadius), 0f)
            assertTrue(resolveBoolean(activity, R.attr.daxMenuClipToOutline))
            assertEquals(28f, resolveDimension(activity, R.attr.daxOnboardingSheetRadius), 0f)
            assertEquals(28f, resolveDimension(activity, R.attr.daxInfoPanelRadius), 0f)
            assertEquals(48f, resolveDimension(activity, R.attr.daxMessageCtaCloseButtonSize), 0f)
            assertEquals(
                R.drawable.selectable_message_cta_close_button_ripple,
                resolveResourceId(activity, R.attr.daxMessageCtaCloseButtonBackground),
            )
            assertFalse(resolveBoolean(activity, R.attr.daxMessageCtaClipToPadding))
            assertEquals(1000f, resolveDimension(activity, R.attr.daxPillRadius), 0f)
        }
    }

    @Test
    fun whenFixedThemeActivityThenRadiusValuesResolveToRebrandValues() {
        val activity = fixedThemeActivity()

        assertEquals(28f, resolveDimension(activity, R.attr.daxContainerRadius), 0f)
        assertEquals(48f, resolveDimension(activity, R.attr.daxSearchBarRadius), 0f)
    }

    @Test
    fun whenAtNonDefaultDensityThenRadiusScalesWithDensity() {
        val activity = fixedThemeActivity()
        val configuration = Configuration(activity.resources.configuration).apply {
            densityDpi = DisplayMetrics.DENSITY_XHIGH
        }
        val densityResources = activity.createConfigurationContext(configuration).resources
        val value = TypedValue()
        assertTrue(activity.theme.resolveAttribute(R.attr.daxContainerRadius, value, true))

        assertEquals(56f, densityResources.getDimension(value.resourceId), 0f)
    }

    @Test
    @Config(qualifiers = "xhdpi")
    fun whenThemedThenSnackbarCornersAre16DpInRegularAndFireThemes() {
        listOf(DuckDuckGoTheme.LIGHT, DuckDuckGoTheme.DARK).forEach { theme ->
            listOf(false, true).forEach { fireTheme ->
                val activity = fireAwareActivity(theme, fireTheme)
                val background = Snackbar.make(activity.findViewById(android.R.id.content), "Message", Snackbar.LENGTH_SHORT)
                    .view.background as MaterialShapeDrawable
                val shape = background.shapeAppearanceModel
                val expectedRadius = 16f * activity.resources.displayMetrics.density

                listOf(shape.topLeftCornerSize, shape.topRightCornerSize, shape.bottomLeftCornerSize, shape.bottomRightCornerSize)
                    .forEach { corner -> assertEquals(expectedRadius, corner.getCornerSize(RectF(0f, 0f, 100f, 100f)), 0f) }
            }
        }
    }

    @Test
    fun whenLightFireInputModeCardThenMenuRadiusSurvivesCardTheme() {
        val activity = fixedThemeActivity(themeResId = R.style.Theme_DuckDuckGo_Light_Fire)
        val cardOverlay = resolveResourceId(activity, R.attr.daxInputModeCardThemeOverlay)
        val card = ContextThemeWrapper(activity, cardOverlay)

        assertEquals(R.style.ThemeOverlay_DuckDuckGo_InputModeCard_DarkFire, cardOverlay)
        assertEquals(16f, resolveDimension(card, R.attr.daxMenuRadius), 0f)
        assertTrue(resolveBoolean(card, R.attr.daxMenuClipToOutline))
    }

    @Test
    fun whenLightThemeThenAccentBlueIsPondwater60() {
        val activity = themedActivity()
        assertEquals(colorOf(R.color.pondwater60), resolveColor(activity, R.attr.daxColorAccentBlue))
    }

    @Test
    fun whenDarkThemeThenAccentBlueIsPondwater40() {
        val activity = themedActivity(DuckDuckGoTheme.DARK)
        assertEquals(colorOf(R.color.pondwater40), resolveColor(activity, R.attr.daxColorAccentBlue))
    }

    @Test
    fun whenThemedThenSwitchTrackFollowsAccentBlue() {
        val activity = themedActivity()
        assertEquals(
            resolveColor(activity, R.attr.daxColorAccentBlue),
            resolveColor(activity, R.attr.daxColorSwitchTrackOn),
        )
    }

    @Test
    fun whenThemedThenStatusIndicatorOnColorIsRebrandGreen40() {
        val activity = themedActivity()

        assertEquals(
            colorOf(R.color.rb_green40),
            resolveStatusIndicatorColor(activity, enabled = true),
        )
    }

    @Test
    fun whenLightThemeThenStatusIndicatorOffColorIsBlack36() {
        val activity = themedActivity()

        assertEquals(
            colorOf(R.color.black36),
            resolveStatusIndicatorColor(activity, enabled = false),
        )
    }

    @Test
    fun whenDarkThemeThenStatusIndicatorOffColorIsWhite40() {
        val activity = themedActivity(DuckDuckGoTheme.DARK)

        assertEquals(
            colorOf(R.color.white40),
            resolveStatusIndicatorColor(activity, enabled = false),
        )
    }

    private fun resolveStatusIndicatorColor(
        activity: AppCompatActivity,
        enabled: Boolean,
    ): Int {
        val state = if (enabled) android.R.attr.state_enabled else -android.R.attr.state_enabled
        return activity.resources
            .getColorStateList(R.color.status_indicator_color_selector, activity.theme)
            .getColorForState(intArrayOf(state), 0)
    }
}
