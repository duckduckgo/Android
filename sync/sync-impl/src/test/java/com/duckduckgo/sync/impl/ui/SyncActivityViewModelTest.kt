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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.sync.TestSyncFixtures.connectedDevice
import com.duckduckgo.sync.TestSyncFixtures.deviceId
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.qrBitmap
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncRepository
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchDeviceSetupFlow
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.StoreRecoveryCodePDF
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel
import java.lang.String.format
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SyncActivityViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val qrEncoder: QREncoder = mock()
    private val syncRepository: SyncRepository = mock()

    private val testee = SyncActivityViewModel(
        qrEncoder = qrEncoder,
        syncRepository = syncRepository,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenUserSignedInThenDeviceSyncViewStateIsEnabled() = runTest {
        whenever(syncRepository.isSignedIn()).thenReturn(true)

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.isDeviceSyncEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedInThenShowAccount() = runTest {
        whenever(syncRepository.isSignedIn()).thenReturn(true)

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedInThenLoginQRCodeIsNotNull() = runTest {
        val bitmap = qrBitmap()
        whenever(syncRepository.isSignedIn()).thenReturn(true)
        whenever(syncRepository.getRecoveryCode()).thenReturn(jsonRecoveryKeyEncoded)
        whenever(qrEncoder.encodeAsBitmap(eq(jsonRecoveryKeyEncoded), any(), any())).thenReturn(bitmap)

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.loginQRCode != null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenToggleDisabledThenLaunchSetupFlow() = runTest {
        testee.onToggleClicked(false)

        testee.viewState().test {
            val viewState = awaitItem()
            assertFalse(viewState.isDeviceSyncEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenRefreshAndUserSignedInThenDeviceSyncViewStateIsEnabled() = runTest {
        whenever(syncRepository.isSignedIn()).thenReturn(true)

        testee.getSyncState()

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.isDeviceSyncEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenToggleEnabledThenLaunchSetupFlow() = runTest {
        testee.onToggleClicked(true)

        testee.commands().test {
            awaitItem().assertCommandType(LaunchDeviceSetupFlow::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenToggleDisabledThenAskTurnOffSync() = runTest {
        testee.onToggleClicked(false)

        testee.commands().test {
            awaitItem().assertCommandType(Command.AskTurnOffSync::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTurnOffSyncConfirmedThenLogoutLocalDevice() = runTest {
        whenever(syncRepository.getThisConnectedDevice()).thenReturn(connectedDevice)
        whenever(syncRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.onTurnOffSyncConfirmed()

        verify(syncRepository).logout(deviceId)
    }

    @Test
    fun whenLogoutSuccessThenUpdateViewState() = runTest {
        whenever(syncRepository.isSignedIn()).thenReturn(true)
        whenever(syncRepository.getThisConnectedDevice()).thenReturn(connectedDevice)
        whenever(syncRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.viewState().test {
            var viewState = awaitItem()
            assertTrue(viewState.isDeviceSyncEnabled)
            whenever(syncRepository.isSignedIn()).thenReturn(false)
            testee.onTurnOffSyncConfirmed()
            viewState = awaitItem()
            assertFalse(viewState.showAccount)
            assertFalse(viewState.isDeviceSyncEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLogoutErrorThenUpdateViewState() = runTest {
        whenever(syncRepository.isSignedIn()).thenReturn(true)
        whenever(syncRepository.logout(deviceId)).thenReturn(Result.Error(reason = "error"))

        testee.onTurnOffSyncConfirmed()

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.isDeviceSyncEnabled)
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTurnOffSyncCancelledThenDeviceSyncViewStateIsEnabled() = runTest {
        testee.viewState().test {
            var viewState = awaitItem()
            testee.onTurnOffSyncCancelled()
            viewState = awaitItem()
            assertTrue(viewState.isDeviceSyncEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteAccountClickedThenAskDeleteAccount() = runTest {
        testee.onDeleteAccountClicked()

        testee.commands().test {
            awaitItem().assertCommandType(Command.AskDeleteAccount::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteAccountSuccessThenUpdateViewState() = runTest {
        whenever(syncRepository.isSignedIn()).thenReturn(true)
        whenever(syncRepository.getThisConnectedDevice()).thenReturn(connectedDevice)
        whenever(syncRepository.deleteAccount()).thenReturn(Result.Success(true))

        testee.viewState().test {
            var viewState = awaitItem()
            assertTrue(viewState.isDeviceSyncEnabled)
            whenever(syncRepository.isSignedIn()).thenReturn(false)
            testee.onDeleteAccountConfirmed()
            viewState = awaitItem()
            assertFalse(viewState.showAccount)
            assertFalse(viewState.isDeviceSyncEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteAccountErrorThenUpdateViewState() = runTest {
        whenever(syncRepository.isSignedIn()).thenReturn(true)
        whenever(syncRepository.deleteAccount()).thenReturn(Result.Error(reason = "error"))

        testee.onDeleteAccountConfirmed()

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.isDeviceSyncEnabled)
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteAccountConfirmedThenDeleteAccount() = runTest {
        whenever(syncRepository.getThisConnectedDevice()).thenReturn(connectedDevice)
        whenever(syncRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.onDeleteAccountConfirmed()

        verify(syncRepository).deleteAccount()
    }

    @Test
    fun whenDeleteAccountCancelledThenDeviceSyncViewStateIsEnabled() = runTest {
        testee.viewState().test {
            var viewState = awaitItem()
            testee.onDeleteAccountCancelled()
            viewState = awaitItem()
            assertTrue(viewState.isDeviceSyncEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnSaveRecoveryCodeThenEmitSaveRecoveryCodeCommand() = runTest {
        whenever(syncRepository.getRecoveryCode()).thenReturn(jsonRecoveryKeyEncoded)
        testee.commands().test {
            testee.onSaveRecoveryCodeClicked()
            val command = awaitItem()
            assertTrue(command is StoreRecoveryCodePDF)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun Command.assertCommandType(expectedType: KClass<out Command>) {
        assertTrue(format("Unexpected command type: %s", this::class.simpleName), this::class == expectedType)
    }
}
