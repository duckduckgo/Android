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
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.autorestore.RestorePayload
import com.duckduckgo.sync.impl.autorestore.SyncAutoRestoreManager
import com.duckduckgo.sync.impl.ui.setup.SyncRestoreAccountViewModel.Command.AbortFlow
import com.duckduckgo.sync.impl.ui.setup.SyncRestoreAccountViewModel.Command.RestorationComplete
import com.duckduckgo.sync.impl.ui.setup.SyncRestoreAccountViewModel.Command.ShowError
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncRestoreAccountViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncAutoRestoreManager: SyncAutoRestoreManager = mock()
    private val syncAccountRepository: SyncAccountRepository = mock()

    private val testee = SyncRestoreAccountViewModel(
        syncAutoRestoreManager = syncAutoRestoreManager,
        syncAccountRepository = syncAccountRepository,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenPayloadIsNullThenShowError() = runTest {
        whenever(syncAutoRestoreManager.retrieveRecoveryPayload()).thenReturn(null)

        backgroundScope.launch { testee.viewState().collect {} }

        testee.commands().test {
            assertTrue(awaitItem() is ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessCodeSucceedsThenEmitsRestorationComplete() = runTest {
        givenValidPayload()
        whenever(syncAccountRepository.processCode(any(), anyOrNull())).thenReturn(Result.Success(true))

        backgroundScope.launch { testee.viewState().collect {} }

        testee.commands().test {
            assertTrue(awaitItem() is RestorationComplete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessCodeFailsThenShowError() = runTest {
        givenValidPayload()
        whenever(syncAccountRepository.processCode(any(), anyOrNull())).thenReturn(Result.Error(code = 401, reason = "auth failed"))

        backgroundScope.launch { testee.viewState().collect {} }

        testee.commands().test {
            assertTrue(awaitItem() is ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenErrorDialogDismissedThenEmitsAbortFlow() = runTest {
        testee.onErrorDialogDismissed()

        testee.commands().test {
            assertTrue(awaitItem() is AbortFlow)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun givenValidPayload() {
        val payload = RestorePayload(recoveryCode = "recoveryCode", deviceId = "deviceId")
        whenever(syncAutoRestoreManager.retrieveRecoveryPayload()).thenReturn(payload)
        whenever(syncAccountRepository.parseSyncAuthCode(any())).thenReturn(SyncAuthCode.Unknown("recoveryCode"))
    }
}
