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

package com.duckduckgo.app.browser.omnibar.animations.addressbar

import android.content.Context
import android.graphics.drawable.Drawable
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.animations.AddressBarTrackersAnimationManager
import com.duckduckgo.app.browser.api.OmnibarRepository
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.global.model.PrivacyShield.MALICIOUS
import com.duckduckgo.app.global.model.PrivacyShield.PROTECTED
import com.duckduckgo.app.global.model.PrivacyShield.UNKNOWN
import com.duckduckgo.app.global.model.PrivacyShield.UNPROTECTED
import com.duckduckgo.common.ui.store.AppTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LottiePrivacyShieldAnimationHelperResolveShieldAssetTest {

    private val addressBarTrackersAnimationManager = mock<AddressBarTrackersAnimationManager>()
    private val customTabShieldDrawableFactory = mock<CustomTabShieldDrawableFactory>()
    private val testee = LottiePrivacyShieldAnimationHelper(
        appTheme = mock<AppTheme>(),
        addressBarTrackersAnimationManager = addressBarTrackersAnimationManager,
        omnibarRepository = mock<OmnibarRepository>(),
        customTabShieldDrawableFactory = customTabShieldDrawableFactory,
    )

    @Test
    fun whenBrandIconsEnabledAndProtectedThenBoxedColorShield() {
        assertEquals(R.raw.shield_color_24 to true, resolve(PROTECTED))
    }

    @Test
    fun whenBrandIconsEnabledAndUnprotectedThenBoxedAlertShieldRegardlessOfTheme() {
        assertEquals(R.drawable.shield_alert_24 to true, resolve(UNPROTECTED))
        assertEquals(R.drawable.shield_alert_24 to true, resolve(UNPROTECTED, isLightMode = false))
    }

    @Test
    fun whenBrandIconsEnabledAndMaliciousThenBoxedStaticExclamation() {
        assertEquals(R.drawable.exclamation_recolorable_24 to true, resolve(MALICIOUS))
    }

    @Test
    fun whenBrandIconsEnabledAndLightCustomTabThenDoNotTintWholeStaticShield() = runTest {
        val holder: LottieAnimationView = mock()
        val context: Context = mock()
        val drawable: Drawable = mock()
        whenever(addressBarTrackersAnimationManager.isFeatureEnabled()).thenReturn(false)
        whenever(holder.context).thenReturn(context)
        whenever(customTabShieldDrawableFactory.create(any(), eq(R.drawable.shield_alert_24), eq(true))).thenReturn(drawable)

        testee.setAnimationView(
            holder = holder,
            privacyShield = UNPROTECTED,
            viewMode = ViewMode.CustomTab(0, "example.com", "example.com"),
            useLightAnimation = true,
            isAddressBarRebrandEnabled = true,
        )

        verify(holder).setImageDrawable(drawable)
    }

    @Test
    fun whenBrandIconsEnabledAndDarkCustomTabThenDoNotTintWholeStaticShield() = runTest {
        val holder: LottieAnimationView = mock()
        val context: Context = mock()
        val drawable: Drawable = mock()
        whenever(addressBarTrackersAnimationManager.isFeatureEnabled()).thenReturn(false)
        whenever(holder.context).thenReturn(context)
        whenever(customTabShieldDrawableFactory.create(any(), eq(R.drawable.shield_alert_24), eq(false))).thenReturn(drawable)

        testee.setAnimationView(
            holder = holder,
            privacyShield = UNPROTECTED,
            viewMode = ViewMode.CustomTab(0, "example.com", "example.com"),
            useLightAnimation = false,
            isAddressBarRebrandEnabled = true,
        )

        verify(holder).setImageDrawable(drawable)
    }

    @Test
    fun whenBrandIconsEnabledAndNormalBrowserThenUseStaticShieldWithoutCustomTabTint() = runTest {
        val holder: LottieAnimationView = mock()
        whenever(addressBarTrackersAnimationManager.isFeatureEnabled()).thenReturn(false)

        testee.setAnimationView(
            holder = holder,
            privacyShield = UNPROTECTED,
            viewMode = ViewMode.Browser("example.com"),
            useLightAnimation = false,
            isAddressBarRebrandEnabled = true,
        )

        verify(holder).setImageResource(R.drawable.shield_alert_24)
    }

    @Test
    fun whenBrandIconsDisabledAfterEnabledThenRestoreLegacyShield() = runTest {
        val holder: LottieAnimationView = mock()
        val context: Context = mock()
        val drawable: Drawable = mock()
        val customTab = ViewMode.CustomTab(0, "example.com", "example.com")
        whenever(addressBarTrackersAnimationManager.isFeatureEnabled()).thenReturn(false)
        whenever(holder.context).thenReturn(context)
        whenever(customTabShieldDrawableFactory.create(any(), eq(R.drawable.shield_alert_24), eq(false))).thenReturn(drawable)

        testee.setAnimationView(
            holder = holder,
            privacyShield = UNPROTECTED,
            viewMode = customTab,
            useLightAnimation = false,
            isAddressBarRebrandEnabled = true,
        )
        testee.setAnimationView(
            holder = holder,
            privacyShield = UNPROTECTED,
            viewMode = customTab,
            useLightAnimation = false,
            isAddressBarRebrandEnabled = false,
        )

        verify(holder).setAnimation(R.raw.dark_unprotected_shield)
    }

    @Test
    fun whenBrandIconsEnabledAndLegacyCustomTabThenStillUsesBoxedColorShield() {
        assertEquals(
            R.raw.shield_color_24 to true,
            resolve(PROTECTED, isLegacyCustomTab = true),
        )
    }

    @Test
    fun whenBrandIconsDisabledAndProtectedThenLegacyTrackersShieldUnboxed() {
        assertEquals(
            R.raw.address_bar_trackers_animation_shield to false,
            resolve(PROTECTED, brandIconsEnabled = false),
        )
    }

    @Test
    fun whenBrandIconsDisabledAndTrackersAnimationDisabledThenLegacyProtectedShieldLight() {
        assertEquals(
            R.raw.protected_shield to false,
            resolve(PROTECTED, brandIconsEnabled = false, trackersAnimationEnabled = false),
        )
    }

    @Test
    fun whenBrandIconsDisabledAndTrackersAnimationDisabledThenLegacyProtectedShieldDark() {
        assertEquals(
            R.raw.dark_protected_shield to false,
            resolve(PROTECTED, isLightMode = false, brandIconsEnabled = false, trackersAnimationEnabled = false),
        )
    }

    @Test
    fun whenBrandIconsDisabledAndLegacyCustomTabProtectedThenLegacyCustomTabShieldUnboxed() {
        assertEquals(
            R.raw.protected_shield_custom_tab to false,
            resolve(PROTECTED, isLegacyCustomTab = true, brandIconsEnabled = false),
        )
    }

    @Test
    fun whenBrandIconsDisabledAndLegacyCustomTabProtectedDarkThenDarkLegacyCustomTabShieldUnboxed() {
        assertEquals(
            R.raw.dark_protected_shield_custom_tab to false,
            resolve(PROTECTED, isLightMode = false, isLegacyCustomTab = true, brandIconsEnabled = false),
        )
    }

    @Test
    fun whenBrandIconsDisabledAndUnprotectedThenLegacyUnprotectedShieldUnboxed() {
        assertEquals(
            R.raw.unprotected_shield to false,
            resolve(UNPROTECTED, brandIconsEnabled = false),
        )
    }

    @Test
    fun whenBrandIconsDisabledAndUnprotectedDarkThenDarkLegacyUnprotectedShieldUnboxed() {
        assertEquals(
            R.raw.dark_unprotected_shield to false,
            resolve(UNPROTECTED, isLightMode = false, brandIconsEnabled = false),
        )
    }

    @Test
    fun whenBrandIconsDisabledAndMaliciousThenLegacyAlertRedUnboxed() {
        assertEquals(
            R.raw.alert_red to false,
            resolve(MALICIOUS, brandIconsEnabled = false),
        )
    }

    @Test
    fun whenBrandIconsDisabledAndMaliciousDarkThenLegacyAlertRedDarkUnboxed() {
        assertEquals(
            R.raw.alert_red_dark to false,
            resolve(MALICIOUS, isLightMode = false, brandIconsEnabled = false),
        )
    }

    @Test
    fun whenUnknownThenNoAssetRegardlessOfGate() {
        assertNull(resolve(UNKNOWN))
        assertNull(resolve(UNKNOWN, brandIconsEnabled = false))
    }

    private fun resolve(
        privacyShield: PrivacyShield,
        isLightMode: Boolean = true,
        isLegacyCustomTab: Boolean = false,
        brandIconsEnabled: Boolean = true,
        trackersAnimationEnabled: Boolean = true,
    ) = testee.resolveShieldAsset(privacyShield, isLightMode, isLegacyCustomTab, brandIconsEnabled, trackersAnimationEnabled)
}
