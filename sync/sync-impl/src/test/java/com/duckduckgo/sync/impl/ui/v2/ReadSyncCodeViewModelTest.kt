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

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.CancellationReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType.SYNC_CONNECT
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupFailureReason
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import com.duckduckgo.sync.impl.ui.v2.ReadSyncCodeViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.v2.ReadSyncCodeViewModel.Command.StartSyncProcess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReadSyncCodeViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val clipboard = mock<Clipboard>()
    private val syncPixels = mock<SyncPixels>()
    private val codeDispatcher = mock<SyncCodeDispatcher>()

    private val testee = ReadSyncCodeViewModel(
        entryPoint = SyncEntryPoint.SYNC_NEW_ACCOUNT,
        clipboard = clipboard,
        syncPixels = syncPixels,
        codeDispatcher = codeDispatcher,
    )

    @Test
    fun `when a sync code is pasted then the sync process starts with the pasted url as a pasted source`() = runTest {
        whenever(clipboard.pasteFromClipboard()).thenReturn("sync-code")

        testee.commands.test {
            testee.pasteSyncCode()

            val command = awaitItem()
            assertIs<StartSyncProcess>(command)
            assertEquals("sync-code", command.source.code)
            assertEquals(SyncCodeSource.Pasted("sync-code", SyncEntryPoint.SYNC_NEW_ACCOUNT), command.source)

            cancel()
        }
    }

    @Test
    fun `when a sync code is scanned then the sync process starts with the scanned url as a scanned source`() = runTest {
        testee.commands.test {
            testee.processScannedCode("sync-code")

            val command = awaitItem()
            assertIs<StartSyncProcess>(command)
            assertEquals("sync-code", command.source.code)
            assertEquals(SyncCodeSource.Scanned("sync-code", SyncEntryPoint.SYNC_NEW_ACCOUNT), command.source)

            cancel()
        }
    }

    @Test
    fun `when a blank code is pasted then an invalid pasted code message is shown instead of starting the sync process`() = runTest {
        whenever(clipboard.pasteFromClipboard()).thenReturn("")

        testee.commands.test {
            testee.pasteSyncCode()

            val command = awaitItem()
            assertIs<ShowMessage>(command)
            assertEquals(R.string.sync_simplified_scanner_manual_entry_invalid_code_message, command.message)

            expectNoEvents()
            cancel()
        }
        verify(syncPixels).fireSyncSetupCodePastedParseFailure(SYNC_CONNECT, SetupFailureReason.UNRECOGNIZED_CODE)
    }

    @Test
    fun `when the screen is shown then the scan code screen shown pixel is fired`() = runTest {
        testee.onScannerScreenShown()

        verify(syncPixels).fireScanCodeScreenShown(SYNC_CONNECT)
    }

    @Test
    fun `when the manual entry screen is shown then the manual code screen shown pixel is fired`() = runTest {
        testee.onManualEntryScreenShown()

        verify(syncPixels).fireSyncSetupManualCodeScreenShown(SYNC_CONNECT)
    }

    @Test
    fun `when the user leaves before scanning and no exchange is underway then a scanning cancelled pixel is fired`() = runTest {
        whenever(codeDispatcher.isV2ExchangeUnderway()).thenReturn(false)

        testee.onUserCanceled()

        verify(syncPixels).fireSyncSetupAbandoned(SYNC_CONNECT, CancellationReason.SCANNING_CANCELLED)
    }

    @Test
    fun `when the user leaves while an exchange is underway then a cancelled before finished pixel is fired`() = runTest {
        whenever(codeDispatcher.isV2ExchangeUnderway()).thenReturn(true)

        testee.onUserCanceled()

        verify(syncPixels).fireSyncSetupAbandoned(SYNC_CONNECT, CancellationReason.CANCELLED_BEFORE_FINISHED)
    }
}

@OptIn(ExperimentalContracts::class)
private inline fun <reified T> assertIs(value: Any?) {
    contract {
        returns() implies (value is T)
    }
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
