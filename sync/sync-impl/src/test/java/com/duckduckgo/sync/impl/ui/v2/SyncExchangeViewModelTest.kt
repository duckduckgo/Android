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
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.TestSyncFixtures
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_CANCELLED
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAccountRepository.AuthCode
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.CancellationReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType.SYNC_CONNECT
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupFailureReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupPath
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl.ProtocolVersion.V2
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.BitmapWithCode
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.Command.SetFailureResult
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.Command.ShowV2Error
import com.duckduckgo.sync.impl.ui.v2AlreadyPairedError
import com.duckduckgo.sync.impl.ui.v2UpgradeRequiredError
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@RunWith(AndroidJUnit4::class)
class SyncExchangeViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val bitmap = TestSyncFixtures.qrBitmap()
    private val linkingUrl = SyncBarcodeUrl(
        webSafeB64EncodedCode = "encoded-code",
        deviceName = "Device Name",
        protocolVersion = V2,
    ).asUrl()

    private val accountRepository = mock<SyncAccountRepository>()
    private val codeDispatcher = mock<SyncCodeDispatcher>()
    private val pixels = mock<SyncPixels>()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)
    private val qrEncoder = mock<QREncoder>()

    @Before
    fun setup() {
        whenever(codeDispatcher.presentV2()).thenReturn(emptyFlow())
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(bitmap)
    }

    private fun createTestee() = SyncExchangeViewModel(
        accountRepository = accountRepository,
        codeDispatcher = codeDispatcher,
        pixels = pixels,
        syncFeature = syncFeature,
        qrEncoder = qrEncoder,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun `when v2 is disabled then the v1 connect code is shown as a QR code`() = runTest {
        givenV2Disabled()
        whenever(accountRepository.getConnectQR()).thenReturn(Result.Success(AuthCode(qrCode = "raw-code", rawCode = "b64-code")))

        val testee = createTestee()

        testee.viewState.test {
            assertEquals(BitmapWithCode(bitmap, "raw-code", "raw-code"), awaitItem().bitmap)
            verify(qrEncoder).encodeAsBitmap(eq("raw-code"), any(), any())
            verify(codeDispatcher, never()).presentV2()

            cancel()
        }
    }

    @Test
    fun `when v2 is disabled and the connect code cannot be generated then an error is shown`() = runTest {
        givenV2Disabled()
        whenever(accountRepository.getConnectQR()).thenReturn(Result.Error(reason = "boom"))

        val testee = createTestee()

        testee.commands.test {
            val command = awaitItem()
            assertIs<ShowError>(command)
            assertEquals(R.string.sync_connect_generic_error, command.message)
            assertEquals("boom", command.reason)

            cancel()
        }
    }

    @Test
    fun `when only the v2 connect flow flag is enabled then the v1 connect code is shown`() = runTest {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        syncFeature.canShowV2ConnectCode().setRawStoredState(State(false))
        whenever(accountRepository.getConnectQR()).thenReturn(Result.Success(AuthCode(qrCode = "raw-code", rawCode = "b64-code")))

        createTestee()

        verify(accountRepository).getConnectQR()
        verify(codeDispatcher, never()).presentV2()
    }

    @Test
    fun `when v2 is enabled then the v2 linking code is shown as a QR code`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LinkingCodeReady(linkingUrl)))

        val testee = createTestee()

        testee.viewState.test {
            assertEquals(BitmapWithCode(bitmap, linkingUrl, "encoded-code"), awaitItem().bitmap)
            verify(qrEncoder).encodeAsBitmap(eq(linkingUrl), any(), any())
            verify(accountRepository, never()).getConnectQR()

            cancel()
        }
    }

    @Test
    fun `when the devices are already paired then the already paired error is shown`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.AlreadyConnected))

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
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.UpgradeRequired(codeMajor = 2)))

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
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.Failed(reason = "boom")))

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
    fun `when pairing fails then the setup failed pixel is fired`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.Failed(reason = "boom", path = SetupPath.PAIRING)))

        createTestee()

        verify(pixels).fireSyncSetupFailed(SYNC_CONNECT, SetupFailureReason.UNEXPECTED_FAILURE, SetupPath.PAIRING, null, null, null)
    }

    @Test
    fun `when pairing is denied then the setup abandoned pixel is fired`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.Failed(reason = "denied", code = PAIRING_CANCELLED.code)))

        createTestee()

        verify(pixels).fireSyncSetupAbandoned(SYNC_CONNECT, CancellationReason.CONFIRMATION_DENIED)
    }

    @Test
    fun `when pairing is denied then the setup failed pixel is not fired`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.Failed(reason = "denied", code = PAIRING_CANCELLED.code)))

        createTestee()

        verify(pixels, never()).fireSyncSetupFailed(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when the linking code is ready then no setup pixels are fired`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LinkingCodeReady(linkingUrl)))

        createTestee()

        verifyNoInteractions(pixels)
    }

    @Test
    fun `when the error dialog is dismissed then the failure result is set and the screen closes`() = runTest {
        givenV2Enabled()

        val testee = createTestee()

        testee.commands.test {
            testee.onErrorDialogDismissed()
            assertIs<SetFailureResult>(awaitItem())
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    private fun givenV2Enabled() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        syncFeature.canShowV2ConnectCode().setRawStoredState(State(true))
    }

    private fun givenV2Disabled() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(false))
        syncFeature.canShowV2ConnectCode().setRawStoredState(State(false))
    }
}

@OptIn(ExperimentalContracts::class)
private inline fun <reified T> assertIs(value: Any?) {
    contract {
        returns() implies (value is T)
    }
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
