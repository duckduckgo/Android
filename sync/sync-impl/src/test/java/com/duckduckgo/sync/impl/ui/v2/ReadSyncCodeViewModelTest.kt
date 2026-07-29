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
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl.ProtocolVersion
import com.duckduckgo.sync.impl.ui.v2.ReadSyncCodeViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.v2.ReadSyncCodeViewModel.Command.StartSyncProcess
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@RunWith(AndroidJUnit4::class)
class ReadSyncCodeViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val clipboard = mock<Clipboard>()

    private val testee = ReadSyncCodeViewModel(
        clipboard = clipboard,
    )

    @Test
    fun `when a valid sync code is pasted then the sync process starts with the pasted url`() = runTest {
        val url = SyncBarcodeUrl(webSafeB64EncodedCode = "ABC-123").asUrl()
        whenever(clipboard.pasteFromClipboard()).thenReturn(url)

        testee.commands.test {
            testee.pasteSyncCode()

            val command = awaitItem()
            assertIs<StartSyncProcess>(command)
            assertEquals(url, command.syncUrl)

            cancel()
        }
    }

    @Test
    fun `when a valid v2 sync code is pasted then the sync process starts with the pasted url`() = runTest {
        val url = SyncBarcodeUrl(webSafeB64EncodedCode = "ABC-123", protocolVersion = ProtocolVersion.V2).asUrl()
        whenever(clipboard.pasteFromClipboard()).thenReturn(url)

        testee.commands.test {
            testee.pasteSyncCode()

            val command = awaitItem()
            assertIs<StartSyncProcess>(command)
            assertEquals(url, command.syncUrl)

            cancel()
        }
    }

    @Test
    fun `when the pasted content is not a sync code then a message is shown`() = runTest {
        whenever(clipboard.pasteFromClipboard()).thenReturn("not a sync code")

        testee.commands.test {
            testee.pasteSyncCode()

            val command = awaitItem()
            assertIs<ShowMessage>(command)
            assertEquals(R.string.sync_scanner_v2_manual_entry_invalid_code_pasted, command.message)

            cancel()
        }
    }

    @Test
    fun `when a sync url without a code is pasted then a message is shown`() = runTest {
        whenever(clipboard.pasteFromClipboard()).thenReturn(SyncBarcodeUrl.URL_BASE)

        testee.commands.test {
            testee.pasteSyncCode()

            assertIs<ShowMessage>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the clipboard is empty then a message is shown`() = runTest {
        whenever(clipboard.pasteFromClipboard()).thenReturn("")

        testee.commands.test {
            testee.pasteSyncCode()

            assertIs<ShowMessage>(awaitItem())

            cancel()
        }
    }
}

@OptIn(ExperimentalContracts::class)
private inline fun <reified T> assertIs(value: Any?) {
    contract {
        returns() implies (value is T)
    }
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
