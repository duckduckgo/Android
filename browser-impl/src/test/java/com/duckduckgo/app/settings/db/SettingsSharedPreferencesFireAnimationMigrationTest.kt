/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.duckduckgo.app.settings.db

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SettingsSharedPreferencesFireAnimationMigrationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs: SharedPreferences = context.getSharedPreferences(SettingsSharedPreferences.FILENAME, Context.MODE_PRIVATE)

    private val mockToggles = mock<OnboardingBrandDesignUpdateToggles>()
    private val lazyToggles = dagger.Lazy<OnboardingBrandDesignUpdateToggles> { mockToggles }
    private val mockAppBuildConfig = mock<AppBuildConfig>()

    private val testee = SettingsSharedPreferences(context, mockAppBuildConfig, lazyToggles)

    @Before
    fun setup() {
        prefs.edit { clear() }
    }

    @After
    fun teardown() {
        prefs.edit { clear() }
    }

    private fun setFlag(on: Boolean) {
        val toggle: Toggle = mock { on { isEnabled() } doReturn on }
        whenever(mockToggles.fireAnimationUpdate()).thenReturn(toggle)
    }

    private fun primeFireAnimationPref(value: String) {
        prefs.edit { putString(SettingsSharedPreferences.KEY_SELECTED_FIRE_ANIMATION, value) }
    }

    private fun storedFireAnimation(): String? = prefs.getString(SettingsSharedPreferences.KEY_SELECTED_FIRE_ANIMATION, null)

    @Test
    fun whenSetterCalledWithInfernoThenPrefsContainInferno() {
        testee.selectedFireAnimation = FireAnimation.Inferno

        assertEquals("INFERNO", storedFireAnimation())
    }

    @Test
    fun whenSetterCalledWithHeroFireThenPrefsContainHeroFire() {
        testee.selectedFireAnimation = FireAnimation.HeroFire

        assertEquals("HERO_FIRE", storedFireAnimation())
    }

    @Test
    fun whenNoSavedValueAndFlagOnThenGetterReturnsInfernoAndPrefsRemainAbsent() {
        setFlag(on = true)

        val resolved = testee.selectedFireAnimation

        assertEquals(FireAnimation.Inferno, resolved)
        assertNull(storedFireAnimation())
    }

    @Test
    fun whenNoSavedValueAndFlagOffThenGetterReturnsHeroFireAndPrefsRemainAbsent() {
        setFlag(on = false)

        val resolved = testee.selectedFireAnimation

        assertEquals(FireAnimation.HeroFire, resolved)
        assertNull(storedFireAnimation())
    }

    @Test
    fun whenSavedHeroFireAndFlagOnThenGetterReturnsHeroFire() {
        primeFireAnimationPref("HERO_FIRE")
        setFlag(on = true)

        assertEquals(FireAnimation.HeroFire, testee.selectedFireAnimation)
        assertEquals("HERO_FIRE", storedFireAnimation())
    }

    @Test
    fun whenSavedInfernoAndFlagOffThenGetterReturnsHeroFireButPrefsKeepInferno() {
        primeFireAnimationPref("INFERNO")
        setFlag(on = false)

        assertEquals(FireAnimation.HeroFire, testee.selectedFireAnimation)
        assertEquals("INFERNO", storedFireAnimation())
    }

    @Test
    fun whenSavedHeroWaterThenGetterReturnsHeroWaterRegardlessOfFlag() {
        primeFireAnimationPref("HERO_WATER")

        setFlag(on = true)
        assertEquals(FireAnimation.HeroWater, testee.selectedFireAnimation)

        setFlag(on = false)
        assertEquals(FireAnimation.HeroWater, testee.selectedFireAnimation)

        assertEquals("HERO_WATER", storedFireAnimation())
    }
}
