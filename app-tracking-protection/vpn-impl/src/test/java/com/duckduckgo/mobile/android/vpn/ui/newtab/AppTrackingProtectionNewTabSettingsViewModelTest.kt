/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.newtab

import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify

class AppTrackingProtectionNewTabSettingsViewModelTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: AppTrackingProtectionNewTabSettingsViewModel
    private val setting = FakeFeatureToggleFactory.create(NewTabAppTrackingProtectionSectionSetting::class.java)
    private val lifecycleOwner: LifecycleOwner = mock()
    private val pixels: DeviceShieldPixels = mock()

    @Before
    fun setup() {
        testee = AppTrackingProtectionNewTabSettingsViewModel(
            coroutinesTestRule.testDispatcherProvider,
            setting,
            pixels,
        )
    }

    @Test
    fun whenViewCreatedAndSettingEnabledThenViewStateUpdated() = runTest {
        setting.self().setRawStoredState(State(enable = true))
        testee.onCreate(lifecycleOwner)
        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.enabled)
            }
        }
    }

    @Test
    fun whenViewCreatedAndSettingDisabledThenViewStateUpdated() = runTest {
        setting.self().setRawStoredState(State(enable = false))

        testee.onCreate(lifecycleOwner)
        testee.viewState.test {
            expectMostRecentItem().also {
                assertFalse(it.enabled)
            }
        }
    }

    @Test
    fun whenSettingEnabledThenPixelFired() = runTest {
        setting.self().setRawStoredState(State(enable = false))

        testee.onSettingEnabled(true)
        verify(pixels).reportNewTabSectionToggled(true)
    }

    @Test
    fun whenSettingDisabledThenPixelFired() = runTest {
        setting.self().setRawStoredState(State(enable = false))
        testee.onSettingEnabled(false)
        verify(pixels).reportNewTabSectionToggled(false)
    }
}
