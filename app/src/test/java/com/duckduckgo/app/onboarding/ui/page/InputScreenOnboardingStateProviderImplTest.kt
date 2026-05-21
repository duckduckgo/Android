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

package com.duckduckgo.app.onboarding.ui.page

import android.annotation.SuppressLint
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@SuppressLint("DenyListedApi")
class InputScreenOnboardingStateProviderImplTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature = FakeFeatureToggleFactory.create(
        AndroidBrowserConfigFeature::class.java,
    )

    private fun createProvider() = InputScreenOnboardingStateProviderImpl(
        coroutineScope = coroutineRule.testScope,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
        androidBrowserConfigFeature = androidBrowserConfigFeature,
    )

    @Test
    fun whenCreatedAndToggleEnabledThenIsEnabledIsTrue() = runTest {
        androidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = true))
        val provider = createProvider()
        advanceUntilIdle()
        assertTrue(provider.isEnabled.value)
    }

    @Test
    fun whenCreatedAndToggleDisabledThenIsEnabledIsFalse() = runTest {
        androidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = false))
        val provider = createProvider()
        advanceUntilIdle()
        assertFalse(provider.isEnabled.value)
    }

    @Test
    fun whenToggleFlipsAfterCreationAndOnPrivacyConfigDownloadedThenIsEnabledRefreshes() = runTest {
        androidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = false))
        val provider = createProvider()
        advanceUntilIdle()
        assertFalse(provider.isEnabled.value)

        androidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = true))
        provider.onPrivacyConfigDownloaded()
        advanceUntilIdle()

        assertTrue(provider.isEnabled.value)
    }

    @Test
    fun whenToggleFlipsAfterCreationWithoutCallbackThenIsEnabledDoesNotChange() = runTest {
        androidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = false))
        val provider = createProvider()
        advanceUntilIdle()

        androidBrowserConfigFeature.showInputScreenOnboarding().setRawStoredState(Toggle.State(enable = true))
        advanceUntilIdle()

        assertFalse(provider.isEnabled.value)
    }
}
