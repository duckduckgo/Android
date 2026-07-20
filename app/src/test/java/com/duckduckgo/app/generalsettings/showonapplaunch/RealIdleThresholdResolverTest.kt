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

package com.duckduckgo.app.generalsettings.showonapplaunch

import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertEquals
import org.junit.Test

class RealIdleThresholdResolverTest {

    private val feature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val testee = RealIdleThresholdResolver(feature)

    @Test
    fun whenUserSelectedThenReturnsUserSelectedIgnoringRemoteDefault() {
        setRemoteDefault(1800)

        assertEquals(600L, testee.effectiveThresholdSeconds(600L))
    }

    @Test
    fun whenNoUserSelectionThenReturnsRemoteDefault() {
        setRemoteDefault(1800)

        assertEquals(1800L, testee.effectiveThresholdSeconds(null))
    }

    @Test
    fun whenNoUserSelectionAndNoRemoteDefaultThenReturnsHardcodedDefault() {
        // No settings stored -> getSettings() is null.
        assertEquals(FirstScreenHandlerImpl.DEFAULT_IDLE_THRESHOLD_SECONDS, testee.effectiveThresholdSeconds(null))
    }

    @Test
    fun whenNoUserSelectionAndInvalidRemoteSettingsThenReturnsHardcodedDefault() {
        feature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(enable = true, settings = "not-json"))

        assertEquals(FirstScreenHandlerImpl.DEFAULT_IDLE_THRESHOLD_SECONDS, testee.effectiveThresholdSeconds(null))
    }

    private fun setRemoteDefault(seconds: Long) {
        feature.showNTPAfterIdleReturn().setRawStoredState(
            Toggle.State(enable = true, settings = """{"defaultIdleThresholdSeconds":$seconds}"""),
        )
    }
}
