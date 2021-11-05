/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.accessibility.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsSharedPreferences.Companion.FONT_SIZE_DEFAULT
import com.duckduckgo.app.runBlocking
import junit.framework.Assert.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
class AccessibilitySettingsSharedPreferencesTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val testee = AccessibilitySettingsSharedPreferences(context, coroutineRule.testDispatcherProvider, TestCoroutineScope())

    @After
    fun after() {
        clearSharedPrefs()
    }

    @Test
    fun whenDefaultAccessibilitySettingsThenUseSystemFontSize() {
        assertTrue(testee.useSystemFontSize)
    }

    @Test
    fun whenAppFontSizeEnabledThenUseSystemFontSizeDisabled() {
        testee.useSystemFontSize = false

        assertFalse(testee.useSystemFontSize)
    }

    @Test
    fun whenDefaultAccessibilitySettingsThenForceZoomDisabled() {
        assertFalse(testee.forceZoom)
    }

    @Test
    fun whenUserEnablesForceZoomThenForceZoomEnabled() {
        testee.forceZoom = true

        assertTrue(testee.forceZoom)
    }

    @Test
    fun whenUsingSystemFontSizeThenFontSizeIsSystemFontSize() {
        testee.useSystemFontSize = true
        assertEquals(testee.systemFontSize, testee.fontSize)
    }

    @Test
    fun whenUsingAppFontSizeThenFontSizeIsAppFontSize() {
        testee.useSystemFontSize = false
        assertEquals(testee.appFontSize, testee.fontSize)
    }

    @Test
    fun whenSystemFontSizeThenReturnSystemFontSize() {
        assertEquals(systemFontSize(), testee.systemFontSize)
    }

    @Test
    fun whenUsingAppFontSizeIfBiggerThanDefaultThenApplyScaleFactor() {
        testee.appFontSize = 150f
        testee.useSystemFontSize = false

        assertEquals(140f, testee.fontSize)
    }

    @Test
    fun whenUsingAppFontSizeIfLessOrEqualsThanDefaultThenDoNotScale() {
        testee.appFontSize = 100f
        testee.useSystemFontSize = false

        assertEquals(100f, testee.fontSize)
    }

    @Test
    fun whenValuesChangedThenNewChangesEmitted() = coroutineRule.runBlocking {
        var accessibilitySetting = AccessibilitySettings(true, 100f, false)

        testee.settingsFlow().test {
            assertEquals(accessibilitySetting, awaitItem())

            testee.useSystemFontSize = false
            accessibilitySetting = accessibilitySetting.copy(useSystemFontSize = false)
            assertEquals(accessibilitySetting, awaitItem())

            testee.appFontSize = 150f
            accessibilitySetting = accessibilitySetting.copy(fontSize = 140f)
            assertEquals(accessibilitySetting, awaitItem())

            testee.forceZoom = true
            accessibilitySetting = accessibilitySetting.copy(forceZoom = true)
            assertEquals(accessibilitySetting, awaitItem())

            testee.useSystemFontSize = true
            accessibilitySetting = accessibilitySetting.copy(useSystemFontSize = true, fontSize = 100f)
            assertEquals(accessibilitySetting, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    private fun systemFontSize() = context.resources.configuration.fontScale * FONT_SIZE_DEFAULT

    private fun clearSharedPrefs() {
        val prefs = context.getSharedPreferences(AccessibilitySettingsSharedPreferences.FILENAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }
}
