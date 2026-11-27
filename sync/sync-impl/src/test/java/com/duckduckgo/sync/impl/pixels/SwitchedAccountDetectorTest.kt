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

package com.duckduckgo.sync.impl.pixels

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.AccountInfo
import com.duckduckgo.sync.impl.SyncAccountRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SwitchedAccountDetectorTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val accountRepository = mock<SyncAccountRepository>()
    private val syncPixels = mock<SyncPixels>()

    private val testee = SwitchedAccountDetector(
        accountRepository = accountRepository,
        syncPixels = syncPixels,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun `when the login lands on a different account then the switched account pixel is fired`() = runTest {
        givenPrimaryKey("old-primary-key")
        testee.onPairingStarted()
        givenPrimaryKey("new-primary-key")

        testee.onPaired()

        verify(syncPixels).fireUserSwitchedAccount()
    }

    @Test
    fun `when the login lands on the same account then the switched account pixel is not fired`() = runTest {
        givenPrimaryKey("same-primary-key")
        testee.onPairingStarted()

        testee.onPaired()

        verify(syncPixels, never()).fireUserSwitchedAccount()
    }

    @Test
    fun `when the device was signed out before pairing then the switched account pixel is not fired`() = runTest {
        givenPrimaryKey("")
        testee.onPairingStarted()
        givenPrimaryKey("new-primary-key")

        testee.onPaired()

        verify(syncPixels, never()).fireUserSwitchedAccount()
    }

    @Test
    fun `when the device is signed out after pairing then the switched account pixel is not fired`() = runTest {
        givenPrimaryKey("old-primary-key")
        testee.onPairingStarted()
        givenPrimaryKey("")

        testee.onPaired()

        verify(syncPixels, never()).fireUserSwitchedAccount()
    }

    @Test
    fun `when the pairing start was never recorded then the switched account pixel is not fired`() = runTest {
        givenPrimaryKey("new-primary-key")

        testee.onPaired()

        verify(syncPixels, never()).fireUserSwitchedAccount()
    }

    private fun givenPrimaryKey(primaryKey: String) {
        whenever(accountRepository.getAccountInfo()).thenReturn(AccountInfo(primaryKey = primaryKey))
    }
}
