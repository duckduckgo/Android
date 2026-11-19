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
package com.duckduckgo.app.browser.tabs.transition

import androidx.core.content.edit
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class RealNewTabTransitionDevStoreTest {

    private val preferences = InMemorySharedPreferences()
    private val preferencesProvider: SharedPreferencesProvider = mock {
        on { getSharedPreferences(RealNewTabTransitionDevStore.FILENAME) } doReturn preferences
    }
    private val testee = RealNewTabTransitionDevStore(preferencesProvider)

    private val tuned = NewTabTransitionConfig(
        browserOrigin = NewTabOrigin.BELOW_CENTER,
        tabManagerOrigin = NewTabOrigin.RIGHT_OF_CENTER,
        belowCenter = OriginProfile(
            startSizePercent = 50,
            offsetPercent = -20,
            scaleBackPercent = 80,
            dimPercent = 40,
            slideLeftPercent = 0,
            durationMs = 500,
        ),
        rightOfCenter = OriginProfile(
            startSizePercent = 90,
            offsetPercent = 10,
            scaleBackPercent = 85,
            dimPercent = 10,
            slideLeftPercent = 30,
            durationMs = 250,
        ),
        slowMotion = true,
    )

    @Test
    fun configIsTheDefaultWhenNothingIsSaved() {
        assertEquals(NewTabTransitionConfig.DEFAULT, testee.config)
    }

    @Test
    fun savedConfigIsReadBack() {
        testee.config = tuned

        assertEquals(tuned, RealNewTabTransitionDevStore(preferencesProvider).config)
    }

    @Test
    fun unknownSavedOriginFallsBackToTheDefault() {
        testee.config = tuned
        preferences.edit {
            putString("KEY_BROWSER_ORIGIN", "LEFT_OF_CENTER")
        }

        assertEquals(tuned.copy(browserOrigin = NewTabTransitionConfig.DEFAULT.browserOrigin), testee.config)
    }

    @Test
    fun savedValuesOutsideTheSlidersAreBroughtBackIntoThem() {
        preferences.edit {
            putInt("KEY_START_SIZE_BELOW_CENTER", 360)
            putInt("KEY_OFFSET_BELOW_CENTER", -40)
            putInt("KEY_OFFSET_RIGHT_OF_CENTER", 360)
            putInt("KEY_SCALE_BACK_RIGHT_OF_CENTER", 360)
            putInt("KEY_DIM_RIGHT_OF_CENTER", 360)
            putInt("KEY_SLIDE_LEFT_RIGHT_OF_CENTER", 360)
            putInt("KEY_DURATION_RIGHT_OF_CENTER", 1000)
            putInt("KEY_DURATION_BELOW_CENTER", 365)
        }

        val config = testee.config

        assertEquals(100, config.belowCenter.startSizePercent)
        assertEquals(-30, config.belowCenter.offsetPercent)
        assertEquals(360, config.belowCenter.durationMs)
        assertEquals(30, config.rightOfCenter.offsetPercent)
        assertEquals(100, config.rightOfCenter.scaleBackPercent)
        assertEquals(100, config.rightOfCenter.dimPercent)
        assertEquals(50, config.rightOfCenter.slideLeftPercent)
        assertEquals(900, config.rightOfCenter.durationMs)
    }
}
