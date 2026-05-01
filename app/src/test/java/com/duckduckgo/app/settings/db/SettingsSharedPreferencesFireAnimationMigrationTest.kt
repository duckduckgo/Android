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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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
        whenever(mockToggles.self()).thenReturn(toggle)
        whenever(mockToggles.fireAnimationUpdate()).thenReturn(toggle)
    }

    private fun primeFireAnimationPref(value: String) {
        prefs.edit { putString(SettingsSharedPreferences.KEY_SELECTED_FIRE_ANIMATION, value) }
    }

    private fun primeMigrationDone(done: Boolean) {
        prefs.edit { putBoolean(SettingsSharedPreferences.KEY_FIRE_ANIMATION_MIGRATION_DONE, done) }
    }

    private fun storedFireAnimation(): String? = prefs.getString(SettingsSharedPreferences.KEY_SELECTED_FIRE_ANIMATION, null)

    private fun migrationDone(): Boolean = prefs.getBoolean(SettingsSharedPreferences.KEY_FIRE_ANIMATION_MIGRATION_DONE, false)

    @Test
    fun whenSetterCalledWithHeroWaterThenPrefsContainHeroWaterAndMigrationDoneIsTrue() {
        primeMigrationDone(false)

        testee.selectedFireAnimation = FireAnimation.HeroWater

        assertEquals("HERO_WATER", storedFireAnimation())
        assertTrue(migrationDone())
    }

    @Test
    fun whenSetterCalledWithInfernoThenPrefsContainInfernoAndMigrationDoneIsTrue() {
        primeMigrationDone(false)

        testee.selectedFireAnimation = FireAnimation.Inferno

        assertEquals("INFERNO", storedFireAnimation())
        assertTrue(migrationDone())
    }

    @Test
    fun whenSetterCalledWithHeroFireThenPrefsContainHeroFireAndMigrationDoneIsTrue() {
        primeMigrationDone(false)

        testee.selectedFireAnimation = FireAnimation.HeroFire

        assertEquals("HERO_FIRE", storedFireAnimation())
        assertTrue(migrationDone())
    }

    @Test
    fun whenPreRolloutHeroFireUserAndFlagFlipsOnThenPrefsAreMigratedToInfernoAndMigrationDoneIsSet() {
        primeFireAnimationPref("HERO_FIRE")
        primeMigrationDone(false)
        setFlag(on = true)

        val resolved = testee.selectedFireAnimation

        assertEquals(FireAnimation.Inferno, resolved)
        assertEquals("INFERNO", storedFireAnimation())
        assertTrue(migrationDone())
    }

    @Test
    fun whenPreRolloutHeroWaterUserAndFlagFlipsOnThenPrefsUnchangedAndMigrationDoneIsSet() {
        primeFireAnimationPref("HERO_WATER")
        primeMigrationDone(false)
        setFlag(on = true)

        val resolved = testee.selectedFireAnimation

        assertEquals(FireAnimation.HeroWater, resolved)
        assertEquals("HERO_WATER", storedFireAnimation())
        assertTrue(migrationDone())
    }

    @Test
    fun whenPreRolloutHeroAbstractUserAndFlagFlipsOnThenPrefsUnchangedAndMigrationDoneIsSet() {
        primeFireAnimationPref("HERO_ABSTRACT")
        primeMigrationDone(false)
        setFlag(on = true)

        val resolved = testee.selectedFireAnimation

        assertEquals(FireAnimation.HeroAbstract, resolved)
        assertEquals("HERO_ABSTRACT", storedFireAnimation())
        assertTrue(migrationDone())
    }

    @Test
    fun whenPreRolloutNoneUserAndFlagFlipsOnThenPrefsUnchangedAndMigrationDoneIsSet() {
        primeFireAnimationPref("NONE")
        primeMigrationDone(false)
        setFlag(on = true)

        val resolved = testee.selectedFireAnimation

        assertEquals(FireAnimation.None, resolved)
        assertEquals("NONE", storedFireAnimation())
        assertTrue(migrationDone())
    }

    @Test
    fun whenNewInstallWithNullPrefsAndFlagOnThenGetterReturnsInfernoViaImplicitDefaultAndMigrationDoneIsSet() {
        primeMigrationDone(false)
        setFlag(on = true)

        val resolved = testee.selectedFireAnimation

        assertEquals(FireAnimation.Inferno, resolved)
        assertNull(storedFireAnimation())
        assertTrue(migrationDone())
    }

    @Test
    fun whenMigrationAlreadyDoneAndFlagOnAndPrefsHeroFireThenNoMigrationOccurs() {
        primeFireAnimationPref("HERO_FIRE")
        primeMigrationDone(true)
        setFlag(on = true)

        val resolved = testee.selectedFireAnimation

        assertEquals(FireAnimation.HeroFire, resolved)
        assertEquals("HERO_FIRE", storedFireAnimation())
        assertTrue(migrationDone())
    }

    @Test
    fun whenFlagOffAndPrefsInfernoThenPrefsAreRewrittenToHeroFireAndGetterReturnsHeroFire() {
        primeFireAnimationPref("INFERNO")
        primeMigrationDone(true)
        setFlag(on = false)

        val resolved = testee.selectedFireAnimation

        assertEquals(FireAnimation.HeroFire, resolved)
        assertEquals("HERO_FIRE", storedFireAnimation())
        assertTrue(migrationDone())
    }

    @Test
    fun whenFlagOffAndPrefsInfernoThenSecondReadIsHeroFireAndPrefsAreUnchangedFromHeroFire() {
        primeFireAnimationPref("INFERNO")
        primeMigrationDone(true)
        setFlag(on = false)

        val firstRead = testee.selectedFireAnimation
        val storedAfterFirst = storedFireAnimation()
        val secondRead = testee.selectedFireAnimation
        val storedAfterSecond = storedFireAnimation()

        assertEquals(FireAnimation.HeroFire, firstRead)
        assertEquals("HERO_FIRE", storedAfterFirst)
        assertEquals(FireAnimation.HeroFire, secondRead)
        assertEquals("HERO_FIRE", storedAfterSecond)
    }

    @Test
    fun whenFlagOffAndPrefsHeroWaterThenNoRewrite() {
        primeFireAnimationPref("HERO_WATER")
        primeMigrationDone(true)
        setFlag(on = false)

        val resolved = testee.selectedFireAnimation

        assertEquals(FireAnimation.HeroWater, resolved)
        assertEquals("HERO_WATER", storedFireAnimation())
    }

    @Test
    fun whenFlagOffAndPrefsAbsentThenGetterReturnsHeroFireViaImplicitDefaultAndPrefsRemainAbsent() {
        primeMigrationDone(true)
        setFlag(on = false)

        val resolved = testee.selectedFireAnimation

        assertEquals(FireAnimation.HeroFire, resolved)
        assertNull(storedFireAnimation())
    }

    @Test
    fun whenPreRolloutHeroFireUserCyclesFlagOnOffOnThenStaysOnHeroFireAfterRollback() {
        primeFireAnimationPref("HERO_FIRE")
        primeMigrationDone(false)

        // Step 1: flag flips on for the first time.
        setFlag(on = true)
        val afterFirstOn = testee.selectedFireAnimation
        assertEquals(FireAnimation.Inferno, afterFirstOn)
        assertEquals("INFERNO", storedFireAnimation())
        assertTrue(migrationDone())

        // Step 2: flag flips off — rollback rewrites prefs.
        setFlag(on = false)
        val afterOff = testee.selectedFireAnimation
        assertEquals(FireAnimation.HeroFire, afterOff)
        assertEquals("HERO_FIRE", storedFireAnimation())

        // Step 3: flag flips on again — migration_done is true so no re-migration.
        setFlag(on = true)
        val afterSecondOn = testee.selectedFireAnimation
        assertEquals(FireAnimation.HeroFire, afterSecondOn)
        assertEquals("HERO_FIRE", storedFireAnimation())
    }

    @Test
    fun whenUserPicksClassicDuringFlagOnAndFlagCyclesOffOnThenClassicIsSticky() {
        primeFireAnimationPref("HERO_FIRE")
        primeMigrationDone(true)

        setFlag(on = false)
        val afterOff = testee.selectedFireAnimation
        assertEquals(FireAnimation.HeroFire, afterOff)
        assertEquals("HERO_FIRE", storedFireAnimation())

        setFlag(on = true)
        val afterOn = testee.selectedFireAnimation
        assertEquals(FireAnimation.HeroFire, afterOn)
        assertEquals("HERO_FIRE", storedFireAnimation())
    }

    @Test
    fun whenUserPicksNewInfernoDuringFlagOnAndFlagFlipsOffThenRolledBackToHeroFire() {
        primeFireAnimationPref("INFERNO")
        primeMigrationDone(true)
        setFlag(on = false)

        val resolved = testee.selectedFireAnimation

        assertEquals(FireAnimation.HeroFire, resolved)
        assertEquals("HERO_FIRE", storedFireAnimation())
    }

    @Test
    fun whenMigrationDoneIsFalseAndPrefsAreInfernoThenForwardPathRunsAndMigrationDoneIsTrue() {
        // Documents tolerance for the "impossible by construction" state.
        primeFireAnimationPref("INFERNO")
        primeMigrationDone(false)
        setFlag(on = true)

        val resolved = testee.selectedFireAnimation

        assertEquals(FireAnimation.Inferno, resolved)
        assertEquals("INFERNO", storedFireAnimation())
        assertTrue(migrationDone())
    }
}
