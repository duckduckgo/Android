/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.ui.ShowCodeViewModel.Command
import com.duckduckgo.sync.impl.ui.ShowCodeViewModel.ViewState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
internal class ShowCodeViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncAccountRepository: SyncAccountRepository = mock()
    private val clipboard: Clipboard = mock()

    private val testee = ShowCodeViewModel(
        syncAccountRepository,
        clipboard,
        coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenUserClicksOnCopyCodeThenClipboardIsCopied() = runTest {
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(jsonRecoveryKeyEncoded)
        testee.onCopyCodeClicked()
        verify(clipboard).copyToClipboard(jsonRecoveryKeyEncoded)
    }

    @Test
    fun whenViewStateInitializedButNoRecoveryCodeThenShowError() = runTest {
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(null)
        testee.viewState().test {
            awaitItem()
        }
        testee.commands().test {
            assertEquals(Command.Error, awaitItem())
        }
    }

    @Test
    fun whenViewStateInitializedThenViewIsUpdated() = runTest {
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(jsonRecoveryKeyEncoded)
        testee.viewState().test {
            assertEquals(ViewState(jsonRecoveryKeyEncoded), awaitItem())
        }
    }
}
