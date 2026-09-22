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

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment

private const val EXTRA_THEME = "theme"
private const val EXTRA_THEME_RES_ID = "themeResId"
private const val EXTRA_V1_ENABLED = "v1Enabled"
private const val EXTRA_OVERLAYS = "overlays"
private const val EXTRA_FIXED_LOCAL_CLASS_NAME = "fixedLocalClassName"
private const val EXTRA_ENABLED_TOGGLE = "enabledToggle"
private const val TOGGLE_RADIUS = "radius"

private class TestThemingDataStore(override var theme: DuckDuckGoTheme = DuckDuckGoTheme.LIGHT) : ThemingDataStore {
    override fun isCurrentlySelected(theme: DuckDuckGoTheme): Boolean = this.theme == theme
}

private fun configuredToggles(enabled: Set<String>): AppBrandDesignUpdateToggles {
    val themeToggle = enabledToggle(false)
    val radiusToggle = enabledToggle(TOGGLE_RADIUS in enabled)
    return mock<AppBrandDesignUpdateToggles>().also { toggles ->
        whenever(toggles.theme()).thenReturn(themeToggle)
        whenever(toggles.radius()).thenReturn(radiusToggle)
    }
}

private fun enabledToggle(enabled: Boolean): Toggle = mock<Toggle>().also { whenever(it.isEnabled()).thenReturn(enabled) }

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

    class ThemedActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            val theme = DuckDuckGoTheme.valueOf(intent.getStringExtra(EXTRA_THEME) ?: DuckDuckGoTheme.LIGHT.name)
            setTheme(intent.getIntExtra(EXTRA_THEME_RES_ID, R.style.Theme_DuckDuckGo_Dark))
            applyTheme(
                theme = theme,
                applyBrandDesignUpdate = intent.getBooleanExtra(EXTRA_V1_ENABLED, false),
                overlayStyleIds = intent.getIntArrayExtra(EXTRA_OVERLAYS)?.toList().orEmpty(),
            )
            super.onCreate(savedInstanceState)
        }

        override fun getLocalClassName(): String =
            intent.getStringExtra(EXTRA_FIXED_LOCAL_CLASS_NAME) ?: super.getLocalClassName()
    }

    class ToggleAwareActivity : DuckDuckGoActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_DuckDuckGo_Light)
            themingDataStore = TestThemingDataStore()
            appBrandDesignUpdateToggles = configuredToggles(setOfNotNull(intent.getStringExtra(EXTRA_ENABLED_TOGGLE)))
            onCreate(savedInstanceState, daggerInject = false)
        }
    }

    private fun fixedThemeActivity(
        themeResId: Int = R.style.Theme_DuckDuckGo_Dark,
        applyBrandDesignUpdate: Boolean = false,
        overlayStyleIds: List<Int> = emptyList(),
        fixedLocalClassName: String = FIXED_THEME_ACTIVITIES[1],
    ): AppCompatActivity = buildThemedActivity(
        theme = DuckDuckGoTheme.LIGHT,
        themeResId = themeResId,
        applyBrandDesignUpdate = applyBrandDesignUpdate,
        overlayStyleIds = overlayStyleIds,
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

    private fun themedActivity(
        theme: DuckDuckGoTheme = DuckDuckGoTheme.LIGHT,
        applyBrandDesignUpdate: Boolean = false,
        overlayStyleIds: List<Int> = emptyList(),
    ): AppCompatActivity = buildThemedActivity(
        theme = theme,
        applyBrandDesignUpdate = applyBrandDesignUpdate,
        overlayStyleIds = overlayStyleIds,
    )

    private fun buildThemedActivity(
        theme: DuckDuckGoTheme,
        themeResId: Int = R.style.Theme_DuckDuckGo_Dark,
        applyBrandDesignUpdate: Boolean = false,
        overlayStyleIds: List<Int> = emptyList(),
        fixedLocalClassName: String? = null,
    ): AppCompatActivity {
        val intent = Intent().apply {
            putExtra(EXTRA_THEME, theme.name)
            putExtra(EXTRA_THEME_RES_ID, themeResId)
            putExtra(EXTRA_V1_ENABLED, applyBrandDesignUpdate)
            putExtra(EXTRA_OVERLAYS, overlayStyleIds.toIntArray())
            if (fixedLocalClassName != null) putExtra(EXTRA_FIXED_LOCAL_CLASS_NAME, fixedLocalClassName)
        }
        return Robolectric.buildActivity(ThemedActivity::class.java, intent).setup().get()
    }

    private fun toggleAwareActivity(enabledToggle: String): AppCompatActivity {
        val intent = Intent().putExtra(EXTRA_ENABLED_TOGGLE, enabledToggle)
        return Robolectric.buildActivity(ToggleAwareActivity::class.java, intent).setup().get()
    }

    private fun resolveDimension(
        activity: AppCompatActivity,
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
        activity: AppCompatActivity,
        attr: Int,
    ): Int {
        val value = TypedValue()
        assertTrue(activity.theme.resolveAttribute(attr, value, true))
        return value.resourceId
    }

    @Test
    fun whenFixedThemeActivityWithBrandDesignUpdateThenRebrandStyleResolves() {
        val activity = fixedThemeActivity(applyBrandDesignUpdate = true)
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Primary, resolveDaxButtonPrimary(activity))
    }

    @Test
    fun whenFixedThemeActivityWithBrandDesignUpdateThenThemeStaysDark() {
        val activity = fixedThemeActivity(applyBrandDesignUpdate = true)
        assertFalse(resolveBoolean(activity, android.R.attr.isLightTheme))
    }

    @Test
    fun whenFixedThemeActivityWithoutBrandDesignUpdateThenLegacyStyleResolves() {
        val activity = fixedThemeActivity()
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_TextButton_Primary, resolveDaxButtonPrimary(activity))
    }

    @Test
    fun whenApplyThemeWithBrandDesignUpdateThenRebrandStyleResolves() {
        val activity = themedActivity(applyBrandDesignUpdate = true)
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Primary, resolveDaxButtonPrimary(activity))
    }

    @Test
    fun whenApplyThemeWithoutBrandDesignUpdateThenLegacyStyleResolves() {
        val activity = themedActivity()
        assertEquals(R.style.Widget_DuckDuckGo_DaxButton_TextButton_Primary, resolveDaxButtonPrimary(activity))
    }

    @Test
    fun whenFixedThemeActivityThemeDoesNotSupportOverlayThenOverlayIsNotApplied() {
        val activity = fixedThemeActivity(
            themeResId = R.style.Theme_AppCompat_Transparent_NoActionBar,
            applyBrandDesignUpdate = true,
            fixedLocalClassName = FIXED_THEME_ACTIVITIES.first(),
        )
        assertNotEquals(R.style.Widget_DuckDuckGo_DaxButton_Rebrand_Primary, resolveDaxButtonPrimary(activity))
    }

    @Test
    fun whenFixedThemeActivityWithRadiusOverlayThenAllRadiusValuesResolveToRebrandValues() {
        val activity = fixedThemeActivity(
            overlayStyleIds = listOf(R.style.ThemeOverlay_Rebrand_Radius),
        )

        assertEquals(28f, resolveDimension(activity, R.attr.daxContainerRadius), 0f)
        assertEquals(28f, resolveDimension(activity, R.attr.daxDialogRadius), 0f)
        assertEquals(28f, resolveDimension(activity, R.attr.daxSheetRadius), 0f)
        assertEquals(28f, resolveDimension(activity, R.attr.daxLargeSheetRadius), 0f)
        assertEquals(16f, resolveDimension(activity, R.attr.daxMenuRadius), 0f)
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

    @Test
    fun whenOverlayListAbsentThenRadiusValuesRemainBaseValues() {
        val activity = fixedThemeActivity()

        assertEquals(16f, resolveDimension(activity, R.attr.daxContainerRadius), 0f)
        assertEquals(12f, resolveDimension(activity, R.attr.daxDialogRadius), 0f)
        assertEquals(12f, resolveDimension(activity, R.attr.daxSheetRadius), 0f)
        assertEquals(16f, resolveDimension(activity, R.attr.daxLargeSheetRadius), 0f)
        assertEquals(8f, resolveDimension(activity, R.attr.daxMenuRadius), 0f)
        assertFalse(resolveBoolean(activity, R.attr.daxMenuClipToOutline))
        assertEquals(36f, resolveDimension(activity, R.attr.daxOnboardingSheetRadius), 0f)
        assertEquals(8f, resolveDimension(activity, R.attr.daxInfoPanelRadius), 0f)
        assertEquals(40f, resolveDimension(activity, R.attr.daxMessageCtaCloseButtonSize), 0f)
        assertEquals(
            resolveResourceId(activity, android.R.attr.selectableItemBackground),
            resolveResourceId(activity, R.attr.daxMessageCtaCloseButtonBackground),
        )
        assertTrue(resolveBoolean(activity, R.attr.daxMessageCtaClipToPadding))
        assertEquals(2f, resolveDimension(activity, R.attr.daxPillRadius), 0f)
    }

    @Test
    fun whenUnsupportedFixedThemeWithRadiusOverlayThenRadiusValuesAreNotApplied() {
        val activity = fixedThemeActivity(
            themeResId = R.style.Theme_AppCompat_Transparent_NoActionBar,
            overlayStyleIds = listOf(R.style.ThemeOverlay_Rebrand_Radius),
            fixedLocalClassName = FIXED_THEME_ACTIVITIES.first(),
        )

        listOf(
            R.attr.daxContainerRadius,
            R.attr.daxDialogRadius,
            R.attr.daxSheetRadius,
            R.attr.daxLargeSheetRadius,
            R.attr.daxMenuRadius,
            R.attr.daxMenuClipToOutline,
            R.attr.daxOnboardingSheetRadius,
            R.attr.daxInfoPanelRadius,
            R.attr.daxMessageCtaCloseButtonSize,
            R.attr.daxMessageCtaCloseButtonBackground,
            R.attr.daxMessageCtaClipToPadding,
            R.attr.daxPillRadius,
        ).forEach { attr ->
            assertFalse(activity.theme.resolveAttribute(attr, TypedValue(), true))
        }
    }

    @Test
    fun whenLightAndDarkThemesWithRadiusOverlayThenRadiusValuesResolve() {
        listOf(DuckDuckGoTheme.LIGHT, DuckDuckGoTheme.DARK).forEach { theme ->
            val activity = themedActivity(
                theme,
                overlayStyleIds = listOf(R.style.ThemeOverlay_Rebrand_Radius),
            )

            assertEquals(28f, resolveDimension(activity, R.attr.daxContainerRadius), 0f)
            assertEquals(28f, resolveDimension(activity, R.attr.daxDialogRadius), 0f)
            assertEquals(28f, resolveDimension(activity, R.attr.daxSheetRadius), 0f)
            assertEquals(28f, resolveDimension(activity, R.attr.daxLargeSheetRadius), 0f)
            assertEquals(16f, resolveDimension(activity, R.attr.daxMenuRadius), 0f)
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
    fun whenRadiusOverlayAtNonDefaultDensityThenRadiusScalesWithDensity() {
        val activity = fixedThemeActivity(
            overlayStyleIds = listOf(R.style.ThemeOverlay_Rebrand_Radius),
        )
        val configuration = Configuration(activity.resources.configuration).apply {
            densityDpi = DisplayMetrics.DENSITY_XHIGH
        }
        val densityResources = activity.createConfigurationContext(configuration).resources
        val value = TypedValue()
        assertTrue(activity.theme.resolveAttribute(R.attr.daxContainerRadius, value, true))

        assertEquals(56f, densityResources.getDimension(value.resourceId), 0f)
    }

    @Test
    fun whenDuckDuckGoActivityRadiusToggleIsEnabledThenRadiusOverlayIsApplied() {
        val activity = toggleAwareActivity(TOGGLE_RADIUS)

        assertEquals(28f, resolveDimension(activity, R.attr.daxContainerRadius), 0f)
        assertEquals(28f, resolveDimension(activity, R.attr.daxDialogRadius), 0f)
        assertEquals(28f, resolveDimension(activity, R.attr.daxSheetRadius), 0f)
        assertEquals(28f, resolveDimension(activity, R.attr.daxLargeSheetRadius), 0f)
        assertEquals(16f, resolveDimension(activity, R.attr.daxMenuRadius), 0f)
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
        val activity = themedActivity(applyBrandDesignUpdate = true)
        assertEquals(colorOf(R.color.pondwater60), resolveColor(activity, R.attr.daxColorAccentBlue))
    }

    @Test
    fun whenDarkThemeWithBrandDesignUpdateThenAccentBlueIsPondwater40() {
        val activity = themedActivity(DuckDuckGoTheme.DARK, applyBrandDesignUpdate = true)
        assertEquals(colorOf(R.color.pondwater40), resolveColor(activity, R.attr.daxColorAccentBlue))
    }

    @Test
    fun whenLightThemeWithoutBrandDesignUpdateThenAccentBlueIsUnchanged() {
        val activity = themedActivity()
        assertEquals(colorOf(R.color.blue50), resolveColor(activity, R.attr.daxColorAccentBlue))
    }

    @Test
    fun whenDarkThemeWithoutBrandDesignUpdateThenAccentBlueIsUnchanged() {
        val activity = themedActivity(DuckDuckGoTheme.DARK)
        assertEquals(colorOf(R.color.blue30), resolveColor(activity, R.attr.daxColorAccentBlue))
    }

    @Test
    fun whenBrandDesignUpdateThenSwitchTrackFollowsAccentBlue() {
        val activity = themedActivity(applyBrandDesignUpdate = true)
        assertEquals(
            resolveColor(activity, R.attr.daxColorAccentBlue),
            resolveColor(activity, R.attr.daxColorSwitchTrackOn),
        )
    }

    @Test
    fun whenBrandDesignUpdateThenStatusIndicatorOnColorIsRebrandGreen40() {
        val activity = themedActivity(applyBrandDesignUpdate = true)

        assertEquals(
            colorOf(R.color.rb_green40),
            resolveStatusIndicatorColor(activity, enabled = true),
        )
    }

    @Test
    fun whenLightThemeWithBrandDesignUpdateThenStatusIndicatorOffColorIsBlack36() {
        val activity = themedActivity(applyBrandDesignUpdate = true)

        assertEquals(
            colorOf(R.color.black36),
            resolveStatusIndicatorColor(activity, enabled = false),
        )
    }

    @Test
    fun whenDarkThemeWithBrandDesignUpdateThenStatusIndicatorOffColorIsWhite40() {
        val activity = themedActivity(DuckDuckGoTheme.DARK, applyBrandDesignUpdate = true)

        assertEquals(
            colorOf(R.color.white40),
            resolveStatusIndicatorColor(activity, enabled = false),
        )
    }

    @Test
    fun whenWithoutBrandDesignUpdateThenStatusIndicatorOffColorRemainsGray50() {
        val activity = themedActivity()

        assertEquals(
            colorOf(R.color.gray50),
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
