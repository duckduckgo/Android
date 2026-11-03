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
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SettingsSharedPreferencesFireAnimationMigrationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs: SharedPreferences = context.getSharedPreferences(SettingsSharedPreferences.FILENAME, Context.MODE_PRIVATE)

    private val mockAppBuildConfig = mock<AppBuildConfig>()

    private val testee = SettingsSharedPreferences(context, mockAppBuildConfig)

    @Before
    fun setup() {
        prefs.edit { clear() }
    }

    @After
    fun teardown() {
        prefs.edit { clear() }
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
    fun whenNoSavedValueThenGetterReturnsInfernoAndPrefsRemainAbsent() {
        val resolved = testee.selectedFireAnimation

        assertEquals(FireAnimation.Inferno, resolved)
        assertNull(storedFireAnimation())
    }

    @Test
    fun whenSavedHeroFireThenGetterReturnsHeroFire() {
        primeFireAnimationPref("HERO_FIRE")

        assertEquals(FireAnimation.HeroFire, testee.selectedFireAnimation)
        assertEquals("HERO_FIRE", storedFireAnimation())
    }

    @Test
    fun whenSavedInfernoThenGetterReturnsInfernoAndPrefsKeepInferno() {
        primeFireAnimationPref("INFERNO")

        assertEquals(FireAnimation.Inferno, testee.selectedFireAnimation)
        assertEquals("INFERNO", storedFireAnimation())
    }

    @Test
    fun whenSavedUnrecognisedValueThenGetterReturnsInfernoAndPrefsKeepValue() {
        primeFireAnimationPref("NOT_A_FIRE_ANIMATION")

        assertEquals(FireAnimation.Inferno, testee.selectedFireAnimation)
        assertEquals("NOT_A_FIRE_ANIMATION", storedFireAnimation())
    }

    @Test
    fun whenSavedHeroWaterThenGetterReturnsHeroWater() {
        primeFireAnimationPref("HERO_WATER")

        assertEquals(FireAnimation.HeroWater, testee.selectedFireAnimation)
        assertEquals("HERO_WATER", storedFireAnimation())
    }
}
