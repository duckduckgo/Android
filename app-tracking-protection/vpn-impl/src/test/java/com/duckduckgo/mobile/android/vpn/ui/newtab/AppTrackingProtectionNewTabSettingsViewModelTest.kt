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
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class AppTrackingProtectionNewTabSettingsViewModelTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: AppTrackingProtectionNewTabSettingsViewModel
    private val setting: NewTabAppTrackingProtectionSectionSetting = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    @Before
    fun setup() {
        testee = AppTrackingProtectionNewTabSettingsViewModel(
            coroutinesTestRule.testDispatcherProvider,
            setting,
        )
    }

    @Test
    fun whenViewCreatedAndSettingEnabledThenViewStateUpdated() = runTest {
        whenever(setting.self()).thenReturn(
            object : Toggle {
                override fun isEnabled(): Boolean {
                    return true
                }

                override fun setEnabled(state: State) {
                }

                override fun getRawStoredState(): State {
                    return State()
                }
            },
        )
        testee.onCreate(lifecycleOwner)
        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.enabled)
            }
        }
    }

    @Test
    fun whenViewCreatedAndSettingDisabledThenViewStateUpdated() = runTest {
        whenever(setting.self()).thenReturn(
            object : Toggle {
                override fun isEnabled(): Boolean {
                    return false
                }

                override fun setEnabled(state: State) {
                }

                override fun getRawStoredState(): State {
                    return State()
                }
            },
        )
        testee.onCreate(lifecycleOwner)
        testee.viewState.test {
            expectMostRecentItem().also {
                assertFalse(it.enabled)
            }
        }
    }
}
