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

package com.duckduckgo.sync.impl.ui.v2

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.autorestore.RestorePayload
import com.duckduckgo.sync.impl.autorestore.SyncAutoRestoreManager
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.v2.PreviousSessionReadyViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.v2.PreviousSessionReadyViewModel.Command.SetContinueSetupResult
import com.duckduckgo.sync.impl.ui.v2.PreviousSessionReadyViewModel.Command.SetResumeResult
import com.duckduckgo.sync.impl.ui.v2.PreviousSessionReadyViewModel.Command.ShowError
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PreviousSessionReadyViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val restorePayload = RestorePayload(
        recoveryCode = "recovery-code",
        deviceId = "device-id",
    )

    private val syncAutoRestoreManager = mock<SyncAutoRestoreManager>()
    private val syncPixels = mock<SyncPixels>()
    private val syncSetupWideEvent = mock<SyncSetupWideEvent>()

    private val testee = PreviousSessionReadyViewModel(
        "sync_backup",
        syncAutoRestoreManager,
        syncPixels,
        syncSetupWideEvent,
        coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun `when the screen is shown then the ready shown pixel is fired with the source`() = runTest {
        testee.onScreenShown()

        verify(syncPixels).fireAutoRestoreSettingsReadyShown(source = "sync_backup")
    }

    @Test
    fun `when resume is clicked and a payload exists then the resume result is set and the screen closes`() = runTest {
        whenever(syncAutoRestoreManager.retrieveRecoveryPayload()).thenReturn(restorePayload)

        testee.commands.test {
            testee.onResumeClicked()
            assertEquals("recovery-code", (awaitItem() as SetResumeResult).recoveryCode)
            assertIs<Close>(awaitItem())

            cancel()
        }

        verify(syncPixels).fireAutoRestoreSettingsRestoreTapped(source = "sync_backup")
        verify(syncSetupWideEvent).onSyncRestoreStarted()
    }

    @Test
    fun `when resume is clicked and the payload is missing then an error is shown`() = runTest {
        whenever(syncAutoRestoreManager.retrieveRecoveryPayload()).thenReturn(null)

        testee.commands.test {
            testee.onResumeClicked()
            assertIs<ShowError>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when continue setup is clicked then the continue setup result is set and the screen closes`() = runTest {
        testee.commands.test {
            testee.onContinueSetupClicked()
            assertIs<SetContinueSetupResult>(awaitItem())
            assertIs<Close>(awaitItem())

            cancel()
        }

        verify(syncPixels).fireAutoRestoreSettingsSkipRestoreTapped(source = "sync_backup")
    }

    @Test
    fun `when close is clicked then the screen closes`() = runTest {
        testee.commands.test {
            testee.onCloseClicked()
            assertIs<Close>(awaitItem())

            cancel()
        }

        verify(syncPixels).fireAutoRestoreSettingsCancelled(source = "sync_backup")
    }
}

private inline fun <reified T> assertIs(value: Any?) {
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
