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
import com.duckduckgo.app.browser.senseofprotection.SenseOfProtectionExperiment
import com.duckduckgo.app.global.model.PrivacyShield.MALICIOUS
import com.duckduckgo.app.global.model.PrivacyShield.PROTECTED
import com.duckduckgo.app.global.model.PrivacyShield.UNPROTECTED
import com.duckduckgo.common.ui.experiments.visual.store.VisualDesignExperimentDataStore
import com.duckduckgo.common.ui.store.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LottiePrivacyShieldAnimationHelperTest {

    private val senseOfProtectionExperiment: SenseOfProtectionExperiment = mock()
    private val visualDesignExperimentDataStore: VisualDesignExperimentDataStore = mock()
    private val enabledVisualExperimentStateFlow = MutableStateFlow(true)
    private val disabledVisualExperimentStateFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        whenever(visualDesignExperimentDataStore.isExperimentEnabled).thenReturn(
            disabledVisualExperimentStateFlow,
        )
    }

    @Test
    fun whenLightModeAndPrivacyShieldProtectedThenSetLightShieldAnimation() {
        whenever(senseOfProtectionExperiment.shouldShowNewPrivacyShield()).thenReturn(false)

        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme, senseOfProtectionExperiment, visualDesignExperimentDataStore)

        testee.setAnimationView(holder, PROTECTED)

        verify(holder).setAnimation(R.raw.protected_shield)
    }

    @Test
    fun whenDarkModeAndPrivacyShieldProtectedThenSetDarkShieldAnimation() {
        whenever(senseOfProtectionExperiment.shouldShowNewPrivacyShield()).thenReturn(false)

        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme, senseOfProtectionExperiment, visualDesignExperimentDataStore)

        testee.setAnimationView(holder, PROTECTED)

        verify(holder).setAnimation(R.raw.dark_protected_shield)
    }

    @Test
    fun whenLightModeAndPrivacyShieldUnProtectedThenUseLightAnimation() {
        whenever(senseOfProtectionExperiment.shouldShowNewPrivacyShield()).thenReturn(false)

        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme, senseOfProtectionExperiment, visualDesignExperimentDataStore)

        testee.setAnimationView(holder, UNPROTECTED)

        verify(holder).setAnimation(R.raw.unprotected_shield)
        verify(holder).progress = 1.0f
    }

    @Test
    fun whenDarkModeAndPrivacyShieldUnProtectedThenUseDarkAnimation() {
        whenever(senseOfProtectionExperiment.shouldShowNewPrivacyShield()).thenReturn(false)

        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme, senseOfProtectionExperiment, visualDesignExperimentDataStore)

        testee.setAnimationView(holder, UNPROTECTED)

        verify(holder).setAnimation(R.raw.dark_unprotected_shield)
        verify(holder).progress = 1.0f
    }

    @Test
    fun whenLightModeAndPrivacyShieldMaliciousThenUseLightAnimation() {
        whenever(senseOfProtectionExperiment.shouldShowNewPrivacyShield()).thenReturn(false)

        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme, senseOfProtectionExperiment, visualDesignExperimentDataStore)

        testee.setAnimationView(holder, MALICIOUS)

        verify(holder).setAnimation(R.raw.alert_red)
        verify(holder).progress = 0.0f
    }

    @Test
    fun whenDarkModeAndPrivacyShieldMaliciousThenUseDarkAnimation() {
        whenever(senseOfProtectionExperiment.shouldShowNewPrivacyShield()).thenReturn(false)

        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)
        val testee = LottiePrivacyShieldAnimationHelper(appTheme, senseOfProtectionExperiment, visualDesignExperimentDataStore)

        testee.setAnimationView(holder, MALICIOUS)

        verify(holder).setAnimation(R.raw.alert_red_dark)
        verify(holder).progress = 0.0f
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenLightModeAndProtectedAndSelfEnabledAndShouldShowNewShieldThenUseExperimentAssets() {
        whenever(senseOfProtectionExperiment.shouldShowNewPrivacyShield()).thenReturn(true)

        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)

        val testee = LottiePrivacyShieldAnimationHelper(appTheme, senseOfProtectionExperiment, visualDesignExperimentDataStore)

        testee.setAnimationView(holder, PROTECTED)

        verify(holder).setAnimation(R.raw.protected_shield_experiment)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenLightModeAndUnprotectedAndSelfEnabledAndShouldShowNewShieldThenUseExperimentAssets() {
        whenever(senseOfProtectionExperiment.shouldShowNewPrivacyShield()).thenReturn(true)

        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)

        val testee = LottiePrivacyShieldAnimationHelper(appTheme, senseOfProtectionExperiment, visualDesignExperimentDataStore)

        testee.setAnimationView(holder, UNPROTECTED)

        verify(holder).setAnimation(R.raw.unprotected_shield_experiment)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenDarkModeAndProtectedAndSelfEnabledAndShouldShowNewShieldThenUseExperimentAssets() {
        whenever(senseOfProtectionExperiment.shouldShowNewPrivacyShield()).thenReturn(true)

        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)

        val testee = LottiePrivacyShieldAnimationHelper(appTheme, senseOfProtectionExperiment, visualDesignExperimentDataStore)

        testee.setAnimationView(holder, PROTECTED)

        verify(holder).setAnimation(R.raw.protected_shield_experiment)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenDarkModeAndUnprotectedAndSelfEnabledAndShouldShowNewShieldThenUseExperimentAssets() {
        whenever(senseOfProtectionExperiment.shouldShowNewPrivacyShield()).thenReturn(true)

        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)

        val testee = LottiePrivacyShieldAnimationHelper(appTheme, senseOfProtectionExperiment, visualDesignExperimentDataStore)

        testee.setAnimationView(holder, UNPROTECTED)

        verify(holder).setAnimation(R.raw.unprotected_shield_experiment_dark)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenLightModeAndProtectedAndSelfEnabledAndShouldShowNotNewShieldThenUseNonExperimentAssets() {
        whenever(senseOfProtectionExperiment.isUserEnrolledInAVariantAndExperimentEnabled()).thenReturn(false)

        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)

        val testee = LottiePrivacyShieldAnimationHelper(appTheme, senseOfProtectionExperiment, visualDesignExperimentDataStore)

        testee.setAnimationView(holder, PROTECTED)

        verify(holder).setAnimation(R.raw.protected_shield)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenLightModeAndProtectedAndSelfEnabledAndShouldShowNewVisualDesignShieldThenUseExperimentAssets() {
        whenever(senseOfProtectionExperiment.shouldShowNewPrivacyShield()).thenReturn(false)
        whenever(visualDesignExperimentDataStore.isExperimentEnabled).thenReturn(
            enabledVisualExperimentStateFlow,
        )

        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)

        val testee = LottiePrivacyShieldAnimationHelper(appTheme, senseOfProtectionExperiment, visualDesignExperimentDataStore)

        testee.setAnimationView(holder, PROTECTED)

        verify(holder).setAnimation(R.raw.protected_shield_visual_updates)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenLightModeAndUnprotectedAndSelfEnabledAndShouldShowNewVisualDesignShieldThenUseExperimentAssets() {
        whenever(senseOfProtectionExperiment.shouldShowNewPrivacyShield()).thenReturn(false)
        whenever(visualDesignExperimentDataStore.isExperimentEnabled).thenReturn(
            enabledVisualExperimentStateFlow,
        )

        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)

        val testee = LottiePrivacyShieldAnimationHelper(appTheme, senseOfProtectionExperiment, visualDesignExperimentDataStore)

        testee.setAnimationView(holder, UNPROTECTED)

        verify(holder).setAnimation(R.raw.unprotected_shield_visual_updates)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenDarkModeAndProtectedAndSelfEnabledAndShouldShowNewVisualDesignShieldThenUseExperimentAssets() {
        whenever(senseOfProtectionExperiment.shouldShowNewPrivacyShield()).thenReturn(false)
        whenever(visualDesignExperimentDataStore.isExperimentEnabled).thenReturn(
            enabledVisualExperimentStateFlow,
        )

        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)

        val testee = LottiePrivacyShieldAnimationHelper(appTheme, senseOfProtectionExperiment, visualDesignExperimentDataStore)

        testee.setAnimationView(holder, PROTECTED)

        verify(holder).setAnimation(R.raw.dark_protected_shield_visual_updates)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenDarkModeAndUnprotectedAndSelfEnabledAndShouldShowNewVisualDesignShieldThenUseExperimentAssets() {
        whenever(senseOfProtectionExperiment.shouldShowNewPrivacyShield()).thenReturn(false)
        whenever(visualDesignExperimentDataStore.isExperimentEnabled).thenReturn(
            enabledVisualExperimentStateFlow,
        )

        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(false)

        val testee = LottiePrivacyShieldAnimationHelper(appTheme, senseOfProtectionExperiment, visualDesignExperimentDataStore)

        testee.setAnimationView(holder, UNPROTECTED)

        verify(holder).setAnimation(R.raw.dark_unprotected_shield_visual_updates)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenLightModeAndProtectedAndSelfEnabledAndShouldShowNotNewVisualDesignShieldThenUseNonExperimentAssets() {
        whenever(senseOfProtectionExperiment.isUserEnrolledInAVariantAndExperimentEnabled()).thenReturn(false)
        whenever(visualDesignExperimentDataStore.isExperimentEnabled).thenReturn(
            disabledVisualExperimentStateFlow,
        )

        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)

        val testee = LottiePrivacyShieldAnimationHelper(appTheme, senseOfProtectionExperiment, visualDesignExperimentDataStore)

        testee.setAnimationView(holder, PROTECTED)

        verify(holder).setAnimation(R.raw.protected_shield)
    }
}
