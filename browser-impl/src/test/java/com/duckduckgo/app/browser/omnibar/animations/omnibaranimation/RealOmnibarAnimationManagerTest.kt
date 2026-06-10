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

package com.duckduckgo.app.browser.omnibar.animations.omnibaranimation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealOmnibarAnimationManagerTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val mockAndroidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()
    private val mockToggle: Toggle = mock()

    private lateinit var testee: RealOmnibarAnimationManager

    @Before
    fun setup() {
        whenever(mockAndroidBrowserConfigFeature.omnibarAnimation()).thenReturn(mockToggle)
        testee = RealOmnibarAnimationManager(
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            androidBrowserConfigFeature = mockAndroidBrowserConfigFeature,
            isMainProcess = true,
            appCoroutineScope = coroutinesTestRule.testScope,
        )
    }

    @Test
    fun whenFeatureDisabledAndNoSettingsThenDefaultsAreUsed() = runTest {
        assertFalse(testee.isFeatureEnabled())
        assertEquals(400L, testee.getChangeBoundsDuration())
        assertEquals(200L, testee.getFadeDuration())
        assertEquals(1f, testee.getTension())
    }

    @Test
    fun whenFeatureEnabledAndNoSettingsThenDefaultsAreUsed() = runTest {
        whenever(mockToggle.isEnabled()).thenReturn(true)

        testee.onPrivacyConfigDownloaded()

        assertTrue(testee.isFeatureEnabled())
        assertEquals(400L, testee.getChangeBoundsDuration())
        assertEquals(200L, testee.getFadeDuration())
        assertEquals(1f, testee.getTension())
    }

    @Test
    fun whenFeatureEnabledAndValidSettingsThenConfigValuesAreUsed() = runTest {
        whenever(mockToggle.isEnabled()).thenReturn(true)
        whenever(mockToggle.getSettings()).thenReturn(
            JSONObject(
                mapOf(
                    "changeBoundsDuration" to 123L,
                    "fadeDuration" to 456L,
                    "tension" to 7.8,
                ),
            ).toString(),
        )

        testee.onPrivacyConfigDownloaded()

        assertTrue(testee.isFeatureEnabled())
        assertEquals(123L, testee.getChangeBoundsDuration())
        assertEquals(456L, testee.getFadeDuration())
        assertEquals(7.8f, testee.getTension())
    }

    @Test
    fun whenSettingsMalformedThenUseDefaults() = runTest {
        whenever(mockToggle.isEnabled()).thenReturn(true)
        whenever(mockToggle.getSettings()).thenReturn(
            """
            {
              "changeBoundsDuration": "test",
              "fadeDuration": 999,
              "tension": {}
            }
            """.trimIndent(),
        )

        testee.onPrivacyConfigDownloaded()

        assertTrue(testee.isFeatureEnabled())
        assertEquals(400L, testee.getChangeBoundsDuration())
        assertEquals(200L, testee.getFadeDuration())
        assertEquals(1f, testee.getTension())
    }
}
