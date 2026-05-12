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

package com.duckduckgo.sync.impl.ui.setup

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.setup.SyncPreviousSessionReadyViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.setup.SyncPreviousSessionReadyViewModel.Command.ContinueSetup
import com.duckduckgo.sync.impl.ui.setup.SyncPreviousSessionReadyViewModel.Command.StartRestore
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class SyncPreviousSessionReadyViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncPixels: SyncPixels = mock()
    private val syncSetupWideEvent: SyncSetupWideEvent = mock()

    private val testee = SyncPreviousSessionReadyViewModel(
        syncPixels = syncPixels,
        syncSetupWideEvent = syncSetupWideEvent,
    )

    @Test
    fun `onScreenShown fires ready shown pixel`() = runTest {
        testee.onScreenShown(SOURCE)

        verify(syncPixels).fireAutoRestoreSettingsReadyShown(SOURCE)
        verifyNoInteractions(syncSetupWideEvent)
    }

    @Test
    fun `onResumeClicked fires restore tapped pixel and notifies sync setup wide event orchestrator`() = runTest {
        testee.onScreenShown(SOURCE)

        testee.onResumeClicked()

        verify(syncPixels).fireAutoRestoreSettingsRestoreTapped(SOURCE)
        verify(syncSetupWideEvent).onSyncRestoreStarted()
    }

    @Test
    fun `onResumeClicked sends StartRestore command`() = runTest {
        testee.onResumeClicked()

        testee.commands().test {
            assertTrue(awaitItem() is StartRestore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onContinueSetupClicked fires skip restore pixel and sends ContinueSetup command`() = runTest {
        testee.onScreenShown(SOURCE)

        testee.onContinueSetupClicked()

        verify(syncPixels).fireAutoRestoreSettingsSkipRestoreTapped(SOURCE)
        verifyNoInteractions(syncSetupWideEvent)
        testee.commands().test {
            assertTrue(awaitItem() is ContinueSetup)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onCloseClicked fires cancelled pixel and sends Close command`() = runTest {
        testee.onScreenShown(SOURCE)

        testee.onCloseClicked()

        verify(syncPixels).fireAutoRestoreSettingsCancelled(SOURCE)
        verifyNoInteractions(syncSetupWideEvent)
        testee.commands().test {
            assertTrue(awaitItem() is Close)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val SOURCE = "settings"
    }
}
