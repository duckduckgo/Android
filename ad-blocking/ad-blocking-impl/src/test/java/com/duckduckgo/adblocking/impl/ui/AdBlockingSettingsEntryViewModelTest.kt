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

package com.duckduckgo.adblocking.impl.ui

import android.annotation.SuppressLint
import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.adblocking.impl.ui.AdBlockingSettingsEntryViewModel.Command.OpenSettings
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi") // setRawStoredState
class AdBlockingSettingsEntryViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val statusChecker: AdBlockingStatusChecker = mock()
    private val lifecycleOwner: LifecycleOwner = mock()
    private val feature = FakeFeatureToggleFactory.create(AdBlockingExtensionFeature::class.java)

    private fun createViewModel(uxImprovements: Boolean = false): AdBlockingSettingsEntryViewModel {
        feature.adBlockingUXImprovements().setRawStoredState(Toggle.State(remoteEnableState = uxImprovements))
        return AdBlockingSettingsEntryViewModel(
            statusChecker,
            coroutineRule.testDispatcherProvider,
            feature,
        )
    }

    @Test
    fun whenShownInSettingsThenVisible() = runTest {
        whenever(statusChecker.isShownInSettingsFlow()).thenReturn(flowOf(true))

        val viewModel = createViewModel()
        viewModel.onStart(lifecycleOwner)

        viewModel.viewState.test {
            assertTrue(awaitItem().isVisible)
        }
    }

    @Test
    fun whenNotShownInSettingsThenNotVisible() = runTest {
        whenever(statusChecker.isShownInSettingsFlow()).thenReturn(flowOf(false))

        val viewModel = createViewModel()
        viewModel.onStart(lifecycleOwner)

        viewModel.viewState.test {
            assertFalse(awaitItem().isVisible)
        }
    }

    @Test
    fun whenStoppedThenStopsObservingVisibilityChanges() = runTest {
        val shownInSettings = MutableStateFlow(true)
        whenever(statusChecker.isShownInSettingsFlow()).thenReturn(shownInSettings)
        val viewModel = createViewModel()

        viewModel.onStart(lifecycleOwner)
        assertTrue(viewModel.viewState.value.isVisible)

        viewModel.onStop(lifecycleOwner)
        shownInSettings.value = false

        // collection was cancelled on stop, so the change is not observed
        assertTrue(viewModel.viewState.value.isVisible)
    }

    @Test
    fun whenSettingClickedThenSendsOpenSettingsCommand() = runTest {
        val viewModel = createViewModel()

        viewModel.commands().test {
            viewModel.onSettingClicked()

            assertTrue(awaitItem() is OpenSettings)
        }
    }

    @Test
    fun whenSettingClickedAndUxImprovementsDisabledThenOpensLegacyScreen() = runTest {
        val viewModel = createViewModel(uxImprovements = false)

        viewModel.commands().test {
            viewModel.onSettingClicked()

            assertEquals(AdBlockingSettingsNoParams, (awaitItem() as OpenSettings).params)
        }
    }

    @Test
    fun whenSettingClickedAndUxImprovementsEnabledThenOpensV2Screen() = runTest {
        val viewModel = createViewModel(uxImprovements = true)

        viewModel.commands().test {
            viewModel.onSettingClicked()

            assertEquals(AdBlockingSettingsV2NoParams, (awaitItem() as OpenSettings).params)
        }
    }
}
