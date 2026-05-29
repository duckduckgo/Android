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

package com.duckduckgo.app.browser

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@SuppressLint("DenyListedApi") // fake toggle store
class RealDuckDuckGoSerpHostProviderTest {

    private val duckChat: DuckChat = mock()
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature =
        FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private lateinit var testee: DuckDuckGoSerpHostProvider

    @Before
    fun before() {
        testee = RealDuckDuckGoSerpHostProvider(duckChat, androidBrowserConfigFeature)
    }

    @Test
    fun whenFlagEnabledAndAiDisabledThenUseNoAiHost() {
        androidBrowserConfigFeature.noAiSerpHost().setRawStoredState(State(true))
        whenever(duckChat.isEnabled()).thenReturn(false)

        assertTrue(testee.shouldUseNoAiHost())
        assertEquals(AppUrl.Url.NO_AI_HOST, testee.searchHost())
    }

    @Test
    fun whenFlagEnabledAndAiEnabledThenUseDefaultHost() {
        androidBrowserConfigFeature.noAiSerpHost().setRawStoredState(State(true))
        whenever(duckChat.isEnabled()).thenReturn(true)

        assertFalse(testee.shouldUseNoAiHost())
        assertEquals(AppUrl.Url.HOST, testee.searchHost())
    }

    @Test
    fun whenFlagDisabledAndAiDisabledThenUseDefaultHost() {
        androidBrowserConfigFeature.noAiSerpHost().setRawStoredState(State(false))
        whenever(duckChat.isEnabled()).thenReturn(false)

        assertFalse(testee.shouldUseNoAiHost())
        assertEquals(AppUrl.Url.HOST, testee.searchHost())
    }

    @Test
    fun whenFlagDisabledAndAiEnabledThenUseDefaultHost() {
        androidBrowserConfigFeature.noAiSerpHost().setRawStoredState(State(false))
        whenever(duckChat.isEnabled()).thenReturn(true)

        assertFalse(testee.shouldUseNoAiHost())
        assertEquals(AppUrl.Url.HOST, testee.searchHost())
    }
}
