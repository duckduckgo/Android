/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.browser.omnibar.animations

import android.annotation.SuppressLint
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.Omnibar
import com.duckduckgo.app.browser.omnibar.animations.addressbar.LottiePrivacyShieldAnimationHelper
import com.duckduckgo.app.global.model.PrivacyShield.MALICIOUS
import com.duckduckgo.app.global.model.PrivacyShield.PROTECTED
import com.duckduckgo.app.global.model.PrivacyShield.UNPROTECTED
import com.duckduckgo.common.ui.store.AppTheme
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LottiePrivacyShieldAnimationHelperTest {

    private val browserViewMode = Omnibar.ViewMode.Browser("cnn.com")
    private val customTabViewMode = Omnibar.ViewMode.CustomTab(0, "cnn.com", "cnn.com")

    @Test
    fun whenLightModeAndPrivacyShieldProtectedThenSetLightShieldAnimation() = runTest {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme)

        testee.setAnimationView(holder, PROTECTED, browserViewMode)

        verify(holder).setAnimation(R.raw.protected_shield)
    }

    @Test
    fun whenDarkModeAndPrivacyShieldProtectedThenSetDarkShieldAnimation() = runTest {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme)

        testee.setAnimationView(holder, PROTECTED, browserViewMode)

        verify(holder).setAnimation(R.raw.dark_protected_shield)
    }

    @Test
    fun whenLightModeAndPrivacyShieldUnProtectedThenUseLightAnimation() = runTest {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme)

        testee.setAnimationView(holder, UNPROTECTED, browserViewMode)

        verify(holder).setAnimation(R.raw.unprotected_shield)
        verify(holder).progress = 1.0f
    }

    @Test
    fun whenDarkModeAndPrivacyShieldUnProtectedThenUseDarkAnimation() = runTest {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme)

        testee.setAnimationView(holder, UNPROTECTED, browserViewMode)

        verify(holder).setAnimation(R.raw.dark_unprotected_shield)
        verify(holder).progress = 1.0f
    }

    @Test
    fun whenLightModeAndPrivacyShieldMaliciousThenUseLightAnimation() = runTest {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme)

        testee.setAnimationView(holder, MALICIOUS, browserViewMode)

        verify(holder).setAnimation(R.raw.alert_red)
        verify(holder).progress = 0.0f
    }

    @Test
    fun whenDarkModeAndPrivacyShieldMaliciousThenUseDarkAnimation() = runTest {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme)

        testee.setAnimationView(holder, MALICIOUS, browserViewMode)

        verify(holder).setAnimation(R.raw.alert_red_dark)
        verify(holder).progress = 0.0f
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenLightModeAndProtectedAndCustomTabViewModeThenUseCustomTabAssets() = runTest {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)

        val testee = LottiePrivacyShieldAnimationHelper(appTheme)

        testee.setAnimationView(holder, PROTECTED, customTabViewMode)

        verify(holder).setAnimation(R.raw.protected_shield_custom_tab)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenDarkModeAndProtectedAndCustomTabViewModeThenUseCustomTabAssets() = runTest {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)

        val testee = LottiePrivacyShieldAnimationHelper(appTheme)

        testee.setAnimationView(holder, PROTECTED, customTabViewMode)

        verify(holder).setAnimation(R.raw.dark_protected_shield_custom_tab)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenLightModeAndUnprotectedAndCustomTabViewModeThenUseVisualUpdatesAssets() = runTest {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)

        val testee = LottiePrivacyShieldAnimationHelper(appTheme)

        testee.setAnimationView(holder, UNPROTECTED, customTabViewMode)

        verify(holder).setAnimation(R.raw.unprotected_shield)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenDarkModeAndUnprotectedAndCustomTabViewModeThenUseDefaultAssets() = runTest {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)

        val testee = LottiePrivacyShieldAnimationHelper(appTheme)

        testee.setAnimationView(holder, UNPROTECTED, customTabViewMode)

        verify(holder).setAnimation(R.raw.dark_unprotected_shield)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenLightModeAndProtectedAndCustomTabViewModeThenUseDefaultAssets() = runTest {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)

        val testee = LottiePrivacyShieldAnimationHelper(appTheme)

        testee.setAnimationView(holder, PROTECTED, customTabViewMode)

        verify(holder).setAnimation(R.raw.protected_shield_custom_tab)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenDarkModeAndProtectedAndCustomTabViewModeThenUseDefaultAssets() = runTest {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)

        val testee = LottiePrivacyShieldAnimationHelper(appTheme)

        testee.setAnimationView(holder, PROTECTED, customTabViewMode)

        verify(holder).setAnimation(R.raw.dark_protected_shield_custom_tab)
    }
}
