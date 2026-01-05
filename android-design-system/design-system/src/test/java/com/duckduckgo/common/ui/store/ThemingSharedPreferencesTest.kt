/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.common.ui.store

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.ui.DuckDuckGoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class ThemingSharedPreferencesTest {

    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var testee: ThemingSharedPreferences

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = context.getSharedPreferences(
            ThemingSharedPreferences.FILENAME,
            Context.MODE_PRIVATE,
        )
        preferences.edit().clear().commit()
        testee = ThemingSharedPreferences(context)
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun whenThemeSetToLightThenGetReturnsLight() {
        testee.theme = DuckDuckGoTheme.LIGHT

        assertEquals(DuckDuckGoTheme.LIGHT, testee.theme)
    }

    @Test
    fun whenThemeSetToDarkThenGetReturnsDark() {
        testee.theme = DuckDuckGoTheme.DARK

        assertEquals(DuckDuckGoTheme.DARK, testee.theme)
    }

    @Test
    fun whenThemeSetToSystemDefaultThenGetReturnsSystemDefault() {
        testee.theme = DuckDuckGoTheme.SYSTEM_DEFAULT

        assertEquals(DuckDuckGoTheme.SYSTEM_DEFAULT, testee.theme)
    }

    @Test
    fun whenLightThemeSetAndCheckingLightThenIsCurrentlySelectedReturnsTrue() {
        testee.theme = DuckDuckGoTheme.LIGHT

        assertTrue(testee.isCurrentlySelected(DuckDuckGoTheme.LIGHT))
    }

    @Test
    fun whenLightThemeSetAndCheckingDarkThenIsCurrentlySelectedReturnsFalse() {
        testee.theme = DuckDuckGoTheme.LIGHT

        assertFalse(testee.isCurrentlySelected(DuckDuckGoTheme.DARK))
    }

    @Test
    fun whenDarkThemeSetAndCheckingDarkThenIsCurrentlySelectedReturnsTrue() {
        testee.theme = DuckDuckGoTheme.DARK

        assertTrue(testee.isCurrentlySelected(DuckDuckGoTheme.DARK))
    }

    @Test
    fun whenDarkThemeSetAndCheckingLightThenIsCurrentlySelectedReturnsFalse() {
        testee.theme = DuckDuckGoTheme.DARK

        assertFalse(testee.isCurrentlySelected(DuckDuckGoTheme.LIGHT))
    }

    @Test
    fun whenSystemDefaultSetAndCheckingSystemDefaultThenIsCurrentlySelectedReturnsTrue() {
        testee.theme = DuckDuckGoTheme.SYSTEM_DEFAULT

        assertTrue(testee.isCurrentlySelected(DuckDuckGoTheme.SYSTEM_DEFAULT))
    }

    @Test
    fun whenSystemDefaultSetAndCheckingLightThenIsCurrentlySelectedReturnsFalse() {
        // Given: System Default is saved in preferences
        testee.theme = DuckDuckGoTheme.SYSTEM_DEFAULT

        // When: Checking if LIGHT is currently selected
        // Then: Should return false even if system is in light mode
        assertFalse(testee.isCurrentlySelected(DuckDuckGoTheme.LIGHT))
    }

    @Test
    fun whenSystemDefaultSetAndCheckingDarkThenIsCurrentlySelectedReturnsFalse() {
        // Given: System Default is saved in preferences
        testee.theme = DuckDuckGoTheme.SYSTEM_DEFAULT

        // When: Checking if DARK is currently selected
        // Then: Should return false even if system is in dark mode
        assertFalse(testee.isCurrentlySelected(DuckDuckGoTheme.DARK))
    }

    @Test
    fun whenSavedValueIsSystemDefaultThenThemeFromReturnsSystemDefault() {
        val mapper = ThemingSharedPreferences.ThemePrefsMapper()

        val result = mapper.themeFrom(
            value = "SYSTEM_DEFAULT",
            defValue = DuckDuckGoTheme.SYSTEM_DEFAULT,
            isInNightMode = false,
        )

        assertEquals(DuckDuckGoTheme.SYSTEM_DEFAULT, result)
    }

    @Test
    fun whenSavedValueIsLightThenThemeFromReturnsLight() {
        val mapper = ThemingSharedPreferences.ThemePrefsMapper()

        val result = mapper.themeFrom(
            value = "LIGHT",
            defValue = DuckDuckGoTheme.SYSTEM_DEFAULT,
            isInNightMode = false,
        )

        assertEquals(DuckDuckGoTheme.LIGHT, result)
    }

    @Test
    fun whenSavedValueIsDarkThenThemeFromReturnsDark() {
        val mapper = ThemingSharedPreferences.ThemePrefsMapper()

        val result = mapper.themeFrom(
            value = "DARK",
            defValue = DuckDuckGoTheme.SYSTEM_DEFAULT,
            isInNightMode = false,
        )

        assertEquals(DuckDuckGoTheme.DARK, result)
    }

    @Test
    fun whenSavedValueIsNullAndSystemInLightModeThenThemeFromReturnsSystemDefault() {
        val mapper = ThemingSharedPreferences.ThemePrefsMapper()

        val result = mapper.themeFrom(
            value = null,
            defValue = DuckDuckGoTheme.SYSTEM_DEFAULT,
            isInNightMode = false,
        )

        assertEquals(DuckDuckGoTheme.SYSTEM_DEFAULT, result)
    }

    @Test
    fun whenSavedValueIsNullAndSystemInDarkModeThenThemeFromReturnsSystemDefault() {
        val mapper = ThemingSharedPreferences.ThemePrefsMapper()

        val result = mapper.themeFrom(
            value = null,
            defValue = DuckDuckGoTheme.SYSTEM_DEFAULT,
            isInNightMode = true,
        )

        assertEquals(DuckDuckGoTheme.SYSTEM_DEFAULT, result)
    }

    @Test
    fun whenMappingSystemDefaultToPrefValueThenReturnsSystemDefaultString() {
        val mapper = ThemingSharedPreferences.ThemePrefsMapper()

        val result = mapper.prefValue(DuckDuckGoTheme.SYSTEM_DEFAULT)

        assertEquals("SYSTEM_DEFAULT", result)
    }

    @Test
    fun whenMappingLightToPrefValueThenReturnsLightString() {
        val mapper = ThemingSharedPreferences.ThemePrefsMapper()

        val result = mapper.prefValue(DuckDuckGoTheme.LIGHT)

        assertEquals("LIGHT", result)
    }

    @Test
    fun whenMappingDarkToPrefValueThenReturnsDarkString() {
        val mapper = ThemingSharedPreferences.ThemePrefsMapper()

        val result = mapper.prefValue(DuckDuckGoTheme.DARK)

        assertEquals("DARK", result)
    }

    @Test
    fun whenUserExplicitlySelectsLightAfterSystemDefaultThenPreferenceIsUpdated() {
        // Given: System Default is initially saved
        testee.theme = DuckDuckGoTheme.SYSTEM_DEFAULT
        assertEquals(DuckDuckGoTheme.SYSTEM_DEFAULT, testee.theme)

        // When: User explicitly selects LIGHT
        testee.theme = DuckDuckGoTheme.LIGHT

        // Then: Preference should be updated to LIGHT
        assertEquals(DuckDuckGoTheme.LIGHT, testee.theme)
        assertTrue(testee.isCurrentlySelected(DuckDuckGoTheme.LIGHT))
        assertFalse(testee.isCurrentlySelected(DuckDuckGoTheme.SYSTEM_DEFAULT))
    }

    @Test
    fun whenUserExplicitlySelectsDarkAfterSystemDefaultThenPreferenceIsUpdated() {
        // Given: System Default is initially saved
        testee.theme = DuckDuckGoTheme.SYSTEM_DEFAULT
        assertEquals(DuckDuckGoTheme.SYSTEM_DEFAULT, testee.theme)

        // When: User explicitly selects DARK
        testee.theme = DuckDuckGoTheme.DARK

        // Then: Preference should be updated to DARK
        assertEquals(DuckDuckGoTheme.DARK, testee.theme)
        assertTrue(testee.isCurrentlySelected(DuckDuckGoTheme.DARK))
        assertFalse(testee.isCurrentlySelected(DuckDuckGoTheme.SYSTEM_DEFAULT))
    }
}
