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

package com.duckduckgo.browsermode.impl

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SuppressLint("DenyListedApi")
class RealFireModeAvailabilityTest {

    @get:Rule val coroutineRule = CoroutineTestRule()

    private val fireModeFeature: FireModeFeature = FakeFeatureToggleFactory.create(FireModeFeature::class.java)

    private val testee = RealFireModeAvailability(
        fireModeFeature,
        coroutineRule.testDispatcherProvider,
        coroutineRule.testScope,
    )

    @Test
    fun `is unavailable when fireTabs flag is disabled`() {
        fireModeFeature.fireTabs().setRawStoredState(Toggle.State(enable = false))

        assertFalse(testee.isAvailable())
    }

    @Test
    fun `availability is frozen on first computation even if flag flips later`() {
        fireModeFeature.fireTabs().setRawStoredState(Toggle.State(enable = false))
        assertFalse(testee.isAvailable())

        fireModeFeature.fireTabs().setRawStoredState(Toggle.State(enable = true))
        assertFalse(testee.isAvailable())
    }
}
