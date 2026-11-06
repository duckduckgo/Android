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

package com.duckduckgo.remote.messaging.impl.systemsettings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.remote.messaging.impl.systemsettings.SystemSettingsScreen.SAMSUNG_HOME_SCREEN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealSystemSettingsLauncherTest {

    private val context: Context = mock()
    private val testee = RealSystemSettingsLauncher()

    @Test
    fun whenScreenIsSamsungHomeScreenThenStartsSamsungHomeScreenSettingsActivity() {
        testee.launch(context, SAMSUNG_HOME_SCREEN)

        val captor = argumentCaptor<Intent>()
        verify(context).startActivity(captor.capture())
        val intent = captor.firstValue
        assertEquals("com.sec.android.app.launcher", intent.component?.packageName)
        assertEquals("com.android.homescreen.settings.HomeScreenSettingsActivity", intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun whenScreenIsSamsungHomeScreenThenIntentMatchesSamsungSettingsFilter() {
        testee.launch(context, SAMSUNG_HOME_SCREEN)

        val captor = argumentCaptor<Intent>()
        verify(context).startActivity(captor.capture())
        val intent = captor.firstValue
        assertEquals("com.sec.android.intent.action.SEC_APPLICATION_SETTINGS", intent.action)
        assertTrue(intent.hasCategory("com.sec.android.intent.category.SEC_APPLICATION_SETTINGS"))
    }

    @Test
    fun whenScreenIsSamsungHomeScreenThenIntentHighlightsShowSearchOnHomeScreenPreference() {
        testee.launch(context, SAMSUNG_HOME_SCREEN)

        val captor = argumentCaptor<Intent>()
        verify(context).startActivity(captor.capture())
        assertEquals("pref_search_on_home_screen", captor.firstValue.getStringExtra(":settings:fragment_args_key"))
    }

    @Test
    fun whenSamsungActivityIsNotFoundThenOpensSystemSettings() {
        doThrow(ActivityNotFoundException()).doNothing().whenever(context).startActivity(any())

        testee.launch(context, SAMSUNG_HOME_SCREEN)

        val captor = argumentCaptor<Intent>()
        verify(context, times(2)).startActivity(captor.capture())
        val fallback = captor.secondValue
        assertEquals(Settings.ACTION_SETTINGS, fallback.action)
        assertNull(fallback.component)
        assertTrue(fallback.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun whenSamsungActivityThrowsSecurityExceptionThenOpensSystemSettings() {
        doThrow(SecurityException()).doNothing().whenever(context).startActivity(any())

        testee.launch(context, SAMSUNG_HOME_SCREEN)

        val captor = argumentCaptor<Intent>()
        verify(context, times(2)).startActivity(captor.capture())
        assertEquals(Settings.ACTION_SETTINGS, captor.secondValue.action)
    }

    @Test
    fun whenSystemSettingsAlsoFailsThenDoesNotThrow() {
        doThrow(ActivityNotFoundException()).doThrow(ActivityNotFoundException()).whenever(context).startActivity(any())

        testee.launch(context, SAMSUNG_HOME_SCREEN)

        verify(context, times(2)).startActivity(any())
    }
}
