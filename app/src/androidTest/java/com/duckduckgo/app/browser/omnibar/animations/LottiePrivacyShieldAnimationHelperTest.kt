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

import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.model.PrivacyShield.MALICIOUS
import com.duckduckgo.app.global.model.PrivacyShield.PROTECTED
import com.duckduckgo.app.global.model.PrivacyShield.UNPROTECTED
import com.duckduckgo.common.ui.internal.experiments.trackersblocking.AppPersonalityFeature
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LottiePrivacyShieldAnimationHelperTest {

    private val fakeAppPersonalityFeature = FakeFeatureToggleFactory.create(AppPersonalityFeature::class.java)

    @Test
    fun whenLightModeAndPrivacyShieldProtectedThenSetLightShieldAnimation() {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme, fakeAppPersonalityFeature)

        testee.setAnimationView(holder, PROTECTED)

        verify(holder).setAnimation(R.raw.protected_shield)
    }

    @Test
    fun whenDarkModeAndPrivacyShieldProtectedThenSetDarkShieldAnimation() {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme, fakeAppPersonalityFeature)

        testee.setAnimationView(holder, PROTECTED)

        verify(holder).setAnimation(R.raw.dark_protected_shield)
    }

    @Test
    fun whenLightModeAndPrivacyShieldUnProtectedThenUseLightAnimation() {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme, fakeAppPersonalityFeature)

        testee.setAnimationView(holder, UNPROTECTED)

        verify(holder).setAnimation(R.raw.unprotected_shield)
        verify(holder).progress = 1.0f
    }

    @Test
    fun whenDarkModeAndPrivacyShieldUnProtectedThenUseDarkAnimation() {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme, fakeAppPersonalityFeature)

        testee.setAnimationView(holder, UNPROTECTED)

        verify(holder).setAnimation(R.raw.dark_unprotected_shield)
        verify(holder).progress = 1.0f
    }

    @Test
    fun whenLightModeAndPrivacyShieldMaliciousThenUseLightAnimation() {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme, fakeAppPersonalityFeature)

        testee.setAnimationView(holder, MALICIOUS)

        verify(holder).setAnimation(R.raw.alert_red)
        verify(holder).progress = 0.0f
    }

    @Test
    fun whenDarkModeAndPrivacyShieldMaliciousThenUseDarkAnimation() {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme, fakeAppPersonalityFeature)

        testee.setAnimationView(holder, MALICIOUS)

        verify(holder).setAnimation(R.raw.alert_red_dark)
        verify(holder).progress = 0.0f
    }

    @Test
    fun whenLightModeAndProtectedAndSelfEnabledAndVariant1DisabledThenUseExperimentAssets() {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)
        // Variant 2 is enabled
        fakeAppPersonalityFeature.self().setRawStoredState(State(enable = true))
        fakeAppPersonalityFeature.variant2().setRawStoredState(State(enable = true))
        // All other variants are disabled, including Variant 1
        fakeAppPersonalityFeature.variant1().setRawStoredState(State(enable = false))
        fakeAppPersonalityFeature.variant3().setRawStoredState(State(enable = false))
        fakeAppPersonalityFeature.variant4().setRawStoredState(State(enable = false))
        fakeAppPersonalityFeature.variant5().setRawStoredState(State(enable = false))

        val testee = LottiePrivacyShieldAnimationHelper(appTheme, fakeAppPersonalityFeature)

        testee.setAnimationView(holder, PROTECTED)

        verify(holder).setAnimation(R.raw.protected_shield_experiment)
    }

    @Test
    fun whenLightModeAndUnprotectedAndSelfEnabledAndVariant1DisabledThenUseExperimentAssets() {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)
        // Variant 2 is enabled
        fakeAppPersonalityFeature.self().setRawStoredState(State(enable = true))
        fakeAppPersonalityFeature.variant2().setRawStoredState(State(enable = true))
        // All other variants are disabled, including Variant 1
        fakeAppPersonalityFeature.variant1().setRawStoredState(State(enable = false))
        fakeAppPersonalityFeature.variant3().setRawStoredState(State(enable = false))
        fakeAppPersonalityFeature.variant4().setRawStoredState(State(enable = false))
        fakeAppPersonalityFeature.variant5().setRawStoredState(State(enable = false))

        val testee = LottiePrivacyShieldAnimationHelper(appTheme, fakeAppPersonalityFeature)

        testee.setAnimationView(holder, UNPROTECTED)

        verify(holder).setAnimation(R.raw.unprotected_shield_experiment)
    }

    @Test
    fun whenDarkModeAndProtectedAndSelfEnabledAndVariant1DisabledThenUseExperimentAssets() {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)
        // Variant 2 is enabled
        fakeAppPersonalityFeature.self().setRawStoredState(State(enable = true))
        fakeAppPersonalityFeature.variant2().setRawStoredState(State(enable = true))
        // All other variants are disabled, including Variant 1
        fakeAppPersonalityFeature.variant1().setRawStoredState(State(enable = false))
        fakeAppPersonalityFeature.variant3().setRawStoredState(State(enable = false))
        fakeAppPersonalityFeature.variant4().setRawStoredState(State(enable = false))
        fakeAppPersonalityFeature.variant5().setRawStoredState(State(enable = false))

        val testee = LottiePrivacyShieldAnimationHelper(appTheme, fakeAppPersonalityFeature)

        testee.setAnimationView(holder, PROTECTED)

        verify(holder).setAnimation(R.raw.protected_shield_experiment)
    }

    @Test
    fun whenDarkModeAndUnprotectedAndSelfEnabledAndVariant1DisabledThenUseExperimentAssets() {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)
        // Variant 2 is enabled
        fakeAppPersonalityFeature.self().setRawStoredState(State(enable = true))
        fakeAppPersonalityFeature.variant2().setRawStoredState(State(enable = true))
        // All other variants are disabled, including Variant 1
        fakeAppPersonalityFeature.variant1().setRawStoredState(State(enable = false))
        fakeAppPersonalityFeature.variant3().setRawStoredState(State(enable = false))
        fakeAppPersonalityFeature.variant4().setRawStoredState(State(enable = false))
        fakeAppPersonalityFeature.variant5().setRawStoredState(State(enable = false))

        val testee = LottiePrivacyShieldAnimationHelper(appTheme, fakeAppPersonalityFeature)

        testee.setAnimationView(holder, UNPROTECTED)

        verify(holder).setAnimation(R.raw.unprotected_shield_experiment_dark)
    }

    @Test
    fun whenLightModeAndProtectedAndSelfEnabledAndVariant1EnabledThenUseNonExperimentAssets() {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)
        // Variant 1 is enabled
        fakeAppPersonalityFeature.self().setRawStoredState(State(enable = true))
        fakeAppPersonalityFeature.variant1().setRawStoredState(State(enable = true))
        // All other variants are disabled
        fakeAppPersonalityFeature.variant2().setRawStoredState(State(enable = false))
        fakeAppPersonalityFeature.variant3().setRawStoredState(State(enable = false))
        fakeAppPersonalityFeature.variant4().setRawStoredState(State(enable = false))
        fakeAppPersonalityFeature.variant5().setRawStoredState(State(enable = false))

        val testee = LottiePrivacyShieldAnimationHelper(appTheme, fakeAppPersonalityFeature)

        testee.setAnimationView(holder, PROTECTED)

        verify(holder).setAnimation(R.raw.protected_shield)
    }
}
