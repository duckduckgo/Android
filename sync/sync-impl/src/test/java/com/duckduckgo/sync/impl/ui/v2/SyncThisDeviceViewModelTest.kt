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
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.v2.SyncThisDeviceViewModel.Command.AbortSyncing
import com.duckduckgo.sync.impl.ui.v2.SyncThisDeviceViewModel.Command.FinishSyncing
import com.duckduckgo.sync.impl.ui.v2.SyncThisDeviceViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.v2.SyncThisDeviceViewModel.Command.SyncWithAnotherDevice
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SyncThisDeviceViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncAccountRepository = mock<SyncAccountRepository>()
    private val syncPixels = mock<SyncPixels>()
    private val syncSetupWideEvent = mock<SyncSetupWideEvent>()

    private val testee = SyncThisDeviceViewModel(
        syncAccountRepository,
        syncPixels,
        coroutineTestRule.testDispatcherProvider,
        syncSetupWideEvent,
    )

    @Test
    fun `when the user is already signed in then syncing finishes`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        testee.commands.test {
            testee.syncThisDevice(source = null)
            assertIs<FinishSyncing>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the user is already signed in then no account is created`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        testee.commands.test {
            testee.syncThisDevice(source = null)
            skipItems(1)

            verify(syncAccountRepository, never()).createAccount()
            verifyNoInteractions(syncPixels)

            cancel()
        }
    }

    @Test
    fun `when the user is not signed in then an account is created and syncing finishes`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.createAccount()).thenReturn(Result.Success(true))

        testee.commands.test {
            testee.syncThisDevice(source = null)
            assertIs<FinishSyncing>(awaitItem())

            verify(syncAccountRepository).createAccount()
            verify(syncPixels).fireSignupDirectPixel(source = null)

            cancel()
        }
    }

    @Test
    fun `when the user is not signed in then the signup pixel is fired with the source`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.createAccount()).thenReturn(Result.Success(true))

        testee.commands.test {
            testee.syncThisDevice(source = "foo")
            skipItems(1)

            verify(syncPixels).fireSignupDirectPixel(source = "foo")

            cancel()
        }
    }

    @Test
    fun `when an account is created successfully then the account created event is tracked`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.createAccount()).thenReturn(Result.Success(true))

        testee.commands.test {
            testee.syncThisDevice(source = null)
            skipItems(1)

            verify(syncSetupWideEvent).onAccountCreated()

            cancel()
        }
    }

    @Test
    fun `when syncing this device then the sync enabled event is tracked`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        testee.commands.test {
            testee.syncThisDevice(source = null)
            skipItems(1)

            verify(syncSetupWideEvent).onSyncEnabled()

            cancel()
        }
    }

    @Test
    fun `when account creation fails then the account creation failed event is tracked`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.createAccount()).thenReturn(Result.Error(1, ""))

        testee.commands.test {
            testee.syncThisDevice(source = null)
            skipItems(1)

            verify(syncSetupWideEvent).onAccountCreationFailed()

            cancel()
        }
    }

    @Test
    fun `when account creation fails then an error is shown`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.createAccount()).thenReturn(Result.Error(1, ""))

        testee.commands.test {
            testee.syncThisDevice(source = null)
            assertIs<ShowError>(awaitItem())

            verifyNoInteractions(syncPixels)

            cancel()
        }
    }

    @Test
    fun `when syncing this device then the syncing state is shown and then cleared`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)

        testee.viewState.test {
            assertFalse(awaitItem().isSyncing)

            testee.syncThisDevice(source = null)
            assertTrue(awaitItem().isSyncing)
            assertFalse(awaitItem().isSyncing)

            cancel()
        }
    }

    @Test
    fun `when the user chooses to sync with another device then that flow starts`() = runTest {
        testee.commands.test {
            testee.onSyncWithAnotherDeviceClicked()
            assertIs<SyncWithAnotherDevice>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the user closes the screen then syncing is aborted`() = runTest {
        testee.commands.test {
            testee.onCloseClicked()
            assertIs<AbortSyncing>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the user dismisses the error then syncing is aborted`() = runTest {
        testee.commands.test {
            testee.onErrorDismissed()
            assertIs<AbortSyncing>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the user sees the screen then the intro screen shown event is tracked`() = runTest {
        testee.onScreenShown()
        verify(syncSetupWideEvent).onIntroScreenShown()
    }
}

private inline fun <reified T> assertIs(value: Any?) {
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
