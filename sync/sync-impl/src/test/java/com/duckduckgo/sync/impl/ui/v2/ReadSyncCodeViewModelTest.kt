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
    fun `when a sync code is pasted then the sync process starts with the pasted url`() = runTest {
        whenever(clipboard.pasteFromClipboard()).thenReturn("sync-code")

        testee.commands.test {
            testee.pasteSyncCode()

            val command = awaitItem()
            assertIs<StartSyncProcess>(command)
            assertEquals("sync-code", command.syncCode)

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
