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
import com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.DeviceType
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.RecoveryCode
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.RouteDecision
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.SyncCodeType
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupPath
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupRole
import com.duckduckgo.sync.impl.ui.v2.ExchangeSyncCodeViewModel.Command.AskHostConfirmation
import com.duckduckgo.sync.impl.ui.v2.ExchangeSyncCodeViewModel.Command.AskJoinerConfirmation
import com.duckduckgo.sync.impl.ui.v2.ExchangeSyncCodeViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.v2.ExchangeSyncCodeViewModel.Command.RunAcknowledgmentAnimation
import com.duckduckgo.sync.impl.ui.v2.ExchangeSyncCodeViewModel.Command.SetPairingResult
import com.duckduckgo.sync.impl.ui.v2.ExchangeSyncCodeViewModel.Command.ShowPairingAcknowledgement
import com.duckduckgo.sync.impl.ui.v2.ExchangeSyncCodeViewModel.Command.ShowV1Error
import com.duckduckgo.sync.impl.ui.v2.ExchangeSyncCodeViewModel.Command.ShowV2Error
import com.duckduckgo.sync.impl.ui.v2.SyncPairingResult.PairingMethod
import com.duckduckgo.sync.impl.ui.v2.SyncPairingResult.Role
import com.duckduckgo.sync.impl.ui.v2AlreadyPairedError
import com.duckduckgo.sync.impl.ui.v2UpgradeRequiredError
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class ExchangeSyncCodeViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val recoveryAuthCode = SyncAuthCode.Recovery(RecoveryCode(primaryKey = "primary-key", userId = "user-id"))

    private val thisDevice = ConnectedDevice(
        thisDevice = true,
        deviceName = "This Device",
        deviceId = "this-device-id",
        deviceType = DeviceType(),
    )
    private val thisParcelableDevice = ParcelableDevice.fromConnectedDevice(thisDevice)

    private val accountRepository = mock<SyncAccountRepository>()
    private val codeDispatcher = mock<SyncCodeDispatcher>()

    private fun createTestee() = ExchangeSyncCodeViewModel(
        syncUrl = "sync-url",
        accountRepository = accountRepository,
        codeDispatcher = codeDispatcher,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun `when a legacy code is routed then the acknowledgment animation runs and the code is processed`() = runTest {
        givenLegacyCode(recoveryAuthCode)
        givenProcessCodeSucceeds()

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())

            cancel()
        }
        verify(accountRepository).processCode(eq(recoveryAuthCode), anyOrNull())
    }

    @Test
    fun `when processing a legacy code succeeds then the screen does not close until the animation completes`() = runTest {
        givenLegacyCode(recoveryAuthCode)
        givenProcessCodeSucceeds()

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when processing a legacy code succeeds then after the animation a success result with no role is set and the screen closes`() = runTest {
        givenLegacyCode(recoveryAuthCode)
        givenProcessCodeSucceeds()
        givenThisConnectedDevice()

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())

            testee.onAnimationComplete()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Success(thisParcelableDevice, role = null, method = PairingMethod.ScannedCode), command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when processing a legacy code fails then a v1 error is shown`() = runTest {
        givenLegacyCode(recoveryAuthCode)
        whenever(accountRepository.processCode(any(), anyOrNull())).thenReturn(Result.Error(code = LOGIN_FAILED.code, reason = "boom"))

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())

            val command = awaitItem()
            assertIs<ShowV1Error>(command)
            assertEquals(R.string.sync_connect_login_error, command.content.message)
            assertEquals("boom", command.content.reason)

            cancel()
        }
    }

    @Test
    fun `when the v2 linking code is ready then no commands are sent`() = runTest {
        givenV2Outcomes(DispatchOutcome.LinkingCodeReady("linking-code"))

        val testee = createTestee()

        testee.commands.test {
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when the host confirmation is requested then the user is asked to confirm the host`() = runTest {
        givenV2Outcomes(DispatchOutcome.HostConfirmationRequested(peerName = "Other Device"))

        val testee = createTestee()

        testee.commands.test {
            assertEquals(AskHostConfirmation(peerName = "Other Device"), awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the joiner confirmation is requested then the user is asked to confirm the joiner`() = runTest {
        givenV2Outcomes(DispatchOutcome.JoinerConfirmationRequested(peerName = "Other Device"))

        val testee = createTestee()

        testee.commands.test {
            assertEquals(AskJoinerConfirmation(peerName = "Other Device"), awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the login completes then the logged in view state is set`() = runTest {
        givenV2Outcomes(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING))

        val testee = createTestee()

        assertTrue(testee.viewState.value.isLoggedIn)
    }

    @Test
    fun `when the login completes then the screen does not close until the animation completes`() = runTest {
        givenV2Outcomes(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING))

        val testee = createTestee()

        testee.commands.test {
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when the login completes then after the animation the success result is set and the screen closes`() = runTest {
        givenV2Outcomes(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING, myRole = SetupRole.JOINER))
        givenThisConnectedDevice()

        val testee = createTestee()

        testee.commands.test {
            testee.onAnimationComplete()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Success(thisParcelableDevice, Role.Joiner, PairingMethod.ScannedCode), command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the login completes as the elected host then a host success result is set`() = runTest {
        givenV2Outcomes(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING, myRole = SetupRole.HOST))
        givenThisConnectedDevice()

        val testee = createTestee()

        testee.commands.test {
            testee.onAnimationComplete()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Success(thisParcelableDevice, Role.Host, PairingMethod.ScannedCode), command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the login completes without an elected role then a success result with no role is set`() = runTest {
        givenV2Outcomes(DispatchOutcome.LoggedIn(path = SetupPath.RECOVERY))
        givenThisConnectedDevice()

        val testee = createTestee()

        testee.commands.test {
            testee.onAnimationComplete()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Success(thisParcelableDevice, role = null, method = PairingMethod.ScannedCode), command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the login completes but this connected device is missing then a failure result is set`() = runTest {
        givenV2Outcomes(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING, myRole = SetupRole.HOST))

        val testee = createTestee()

        testee.commands.test {
            testee.onAnimationComplete()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Failure, command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the devices are already paired then the already paired error is shown`() = runTest {
        givenV2Outcomes(DispatchOutcome.AlreadyConnected)

        val testee = createTestee()

        testee.commands.test {
            val command = awaitItem()
            assertIs<ShowV2Error>(command)
            assertEquals(v2AlreadyPairedError, command.content)

            cancel()
        }
    }

    @Test
    fun `when the peer requires an app upgrade then the upgrade required error is shown`() = runTest {
        givenV2Outcomes(DispatchOutcome.UpgradeRequired(codeMajor = 2))

        val testee = createTestee()

        testee.commands.test {
            val command = awaitItem()
            assertIs<ShowV2Error>(command)
            assertEquals(v2UpgradeRequiredError, command.content)

            cancel()
        }
    }

    @Test
    fun `when pairing fails then the pairing failed error is shown`() = runTest {
        givenV2Outcomes(DispatchOutcome.Failed(reason = "boom"))

        val testee = createTestee()

        testee.commands.test {
            val command = awaitItem()
            assertIs<ShowV2Error>(command)
            assertEquals(R.string.sync_v2_error_pairing_failed, command.content.title)
            assertEquals(R.string.sync_v2_error_try_again, command.content.message)

            cancel()
        }
    }

    @Test
    fun `when the host is confirmed then the dispatcher is notified and the acknowledgement is shown`() = runTest {
        givenV2Outcomes()

        val testee = createTestee()

        testee.commands.test {
            testee.onHostConfirmed()
            assertIs<ShowPairingAcknowledgement>(awaitItem())

            cancel()
        }
        verify(codeDispatcher).confirmHost()
    }

    @Test
    fun `when the host is denied then the dispatcher is notified`() = runTest {
        givenV2Outcomes()

        val testee = createTestee()

        testee.onHostDenied()

        verify(codeDispatcher).denyHost()
    }

    @Test
    fun `when the joiner is confirmed then the dispatcher is notified and the acknowledgement is shown`() = runTest {
        givenV2Outcomes()

        val testee = createTestee()

        testee.commands.test {
            testee.onJoinerConfirmed()
            assertIs<ShowPairingAcknowledgement>(awaitItem())

            cancel()
        }
        verify(codeDispatcher).confirmJoiner()
    }

    @Test
    fun `when the joiner is denied then the dispatcher is notified`() = runTest {
        givenV2Outcomes()

        val testee = createTestee()

        testee.onJoinerDenied()

        verify(codeDispatcher).denyJoiner()
    }

    @Test
    fun `when the acknowledgement animation is requested then the acknowledgment animation runs`() = runTest {
        givenV2Outcomes()

        val testee = createTestee()

        testee.commands.test {
            testee.runAcknowledgementAnimation()
            assertIs<RunAcknowledgmentAnimation>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the error dialog is dismissed then the failure result is set and the screen closes`() = runTest {
        givenV2Outcomes()

        val testee = createTestee()

        testee.commands.test {
            testee.onErrorDialogDismissed()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Failure, command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    private fun givenProcessCodeSucceeds() {
        whenever(accountRepository.processCode(any(), anyOrNull())).thenReturn(Result.Success(true))
    }

    private fun givenThisConnectedDevice() {
        whenever(accountRepository.getThisConnectedDevice()).thenReturn(thisDevice)
    }

    private fun givenLegacyCode(authCode: SyncAuthCode) {
        whenever(codeDispatcher.route(any())).thenReturn(RouteDecision.Legacy(authCode))
    }

    private fun givenV2Outcomes(vararg outcomes: DispatchOutcome) {
        val flow = if (outcomes.isEmpty()) emptyFlow() else flowOf(*outcomes)
        whenever(codeDispatcher.route(any())).thenReturn(RouteDecision.V2InProgress(SyncCodeType.LINKING, flow))
    }
}

@OptIn(ExperimentalContracts::class)
private inline fun <reified T> assertIs(value: Any?) {
    contract {
        returns() implies (value is T)
    }
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
