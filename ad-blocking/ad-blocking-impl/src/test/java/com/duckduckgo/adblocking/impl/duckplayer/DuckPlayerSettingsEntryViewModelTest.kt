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

package com.duckduckgo.adblocking.impl.duckplayer

import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer.DuckPlayerState.DISABLED
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer.DuckPlayerState.DISABLED_WIH_HELP_LINK
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer.DuckPlayerState.ENABLED
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerPixelName.DUCK_PLAYER_SETTINGS_PRESSED
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerSettingsEntryViewModel.Command.OpenSettings
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuckPlayerSettingsEntryViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val duckPlayer: DuckPlayerInternal = mock()
    private val statusChecker: AdBlockingStatusChecker = mock()
    private val pixel: Pixel = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private fun createViewModel() = DuckPlayerSettingsEntryViewModel(
        duckPlayer,
        statusChecker,
        pixel,
        coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenAdBlockingNotShownAndDuckPlayerEnabledThenVisible() = runTest {
        whenever(duckPlayer.observeDuckPlayerState()).thenReturn(flowOf(ENABLED))
        whenever(statusChecker.isShownInSettingsFlow()).thenReturn(flowOf(false))

        val viewModel = createViewModel()
        viewModel.onStart(lifecycleOwner)

        viewModel.viewState.test {
            assertTrue(awaitItem().isVisible)
        }
    }

    @Test
    fun whenAdBlockingNotShownAndDuckPlayerDisabledWithHelpLinkThenVisible() = runTest {
        whenever(duckPlayer.observeDuckPlayerState()).thenReturn(flowOf(DISABLED_WIH_HELP_LINK))
        whenever(statusChecker.isShownInSettingsFlow()).thenReturn(flowOf(false))

        val viewModel = createViewModel()
        viewModel.onStart(lifecycleOwner)

        viewModel.viewState.test {
            assertTrue(awaitItem().isVisible)
        }
    }

    @Test
    fun whenAdBlockingShownThenNotVisible() = runTest {
        whenever(duckPlayer.observeDuckPlayerState()).thenReturn(flowOf(ENABLED))
        whenever(statusChecker.isShownInSettingsFlow()).thenReturn(flowOf(true))

        val viewModel = createViewModel()
        viewModel.onStart(lifecycleOwner)

        viewModel.viewState.test {
            assertFalse(awaitItem().isVisible)
        }
    }

    @Test
    fun whenDuckPlayerDisabledThenNotVisible() = runTest {
        whenever(duckPlayer.observeDuckPlayerState()).thenReturn(flowOf(DISABLED))
        whenever(statusChecker.isShownInSettingsFlow()).thenReturn(flowOf(false))

        val viewModel = createViewModel()
        viewModel.onStart(lifecycleOwner)

        viewModel.viewState.test {
            assertFalse(awaitItem().isVisible)
        }
    }

    @Test
    fun whenSettingClickedThenSendsOpenSettingsCommand() = runTest {
        whenever(duckPlayer.wasUsedBefore()).thenReturn(true)
        val viewModel = createViewModel()

        viewModel.commands().test {
            viewModel.onSettingClicked()

            assertTrue(awaitItem() is OpenSettings)
        }
    }

    @Test
    fun whenSettingClickedThenFiresPixelWithWasUsedBefore() = runTest {
        whenever(duckPlayer.wasUsedBefore()).thenReturn(true)
        val viewModel = createViewModel()

        viewModel.onSettingClicked()

        verify(pixel).fire(DUCK_PLAYER_SETTINGS_PRESSED, mapOf("was_used_before" to "1"), emptyMap(), Count)
    }
}
