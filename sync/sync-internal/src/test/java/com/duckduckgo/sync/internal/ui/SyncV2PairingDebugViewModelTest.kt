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

package com.duckduckgo.sync.internal.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.RouteDecision
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.SyncCodeType
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.Hello
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Runner
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State
import com.duckduckgo.sync.impl.exchange.v2.LocalTrigger
import com.duckduckgo.sync.impl.exchange.v2.PairingRole
import com.duckduckgo.sync.impl.exchange.v2.RealAdvertisedExchangeV2Version
import com.duckduckgo.sync.impl.exchange.v2.RejectReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupPath
import com.duckduckgo.sync.internal.exchange.SyncInternalAdvertisedExchangeV2Version
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SyncV2PairingDebugViewModelTest {

    @get:Rule val coroutineTestRule = CoroutineTestRule()

    private val eventFlow = MutableSharedFlow<ExchangeV2Event>(replay = 0, extraBufferCapacity = 100)
    private val runner: ExchangeV2Runner = mock<ExchangeV2Runner>().also {
        whenever(it.events).thenReturn(eventFlow)
        whenever(it.currentState).thenReturn(null)
    }
    private val syncStore: SyncStore = mock()
    private val syncAccountRepository: SyncAccountRepository = mock()
    private val dispatcher: SyncCodeDispatcher = mock()
    private val realAdvertisedVersion: RealAdvertisedExchangeV2Version = mock<RealAdvertisedExchangeV2Version>().also {
        whenever(it.resolve()).thenReturn(ExchangeProtocolVersion.V2_0)
    }
    private val internalAdvertisedVersion = SyncInternalAdvertisedExchangeV2Version(realAdvertisedVersion)

    private fun newViewModel() = SyncV2PairingDebugViewModel(
        runner = runner,
        syncStore = syncStore,
        syncAccountRepository = syncAccountRepository,
        dispatcher = dispatcher,
        internalAdvertisedVersion = internalAdvertisedVersion,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        appScope = coroutineTestRule.testScope,
    )

    private fun hostDone(reason: ExchangeV2Message.RecoveryCodeDone.Reason) = ExchangeV2Event.Transition(
        timestampMs = 0L,
        from = ExchangeV2State.Host.AwaitingStatus,
        to = ExchangeV2State.Host.Done,
        trigger = ExchangeV2Message.RecoveryCodeDone.create(reason),
        localTrigger = null,
    )

    private fun joinerTerminal(
        to: ExchangeV2State,
        localTrigger: LocalTrigger = LocalTrigger.JoinerJoinComplete(ExchangeV2Message.RecoveryCodeDone.Reason.Success),
    ) = ExchangeV2Event.Transition(
        timestampMs = 0L,
        from = ExchangeV2State.Joiner.Joining,
        to = to,
        trigger = null,
        localTrigger = localTrigger,
    )

    // ---- Event log ----

    @Test fun `Transition event appended to rows with correct summary`() = runTest {
        val viewModel = newViewModel()
        viewModel.viewState().test {
            assertEquals(emptyList<SyncV2PairingDebugViewModel.LogRow>(), awaitItem().rows)

            whenever(runner.currentState).thenReturn(ExchangeV2State.Negotiating)
            eventFlow.emit(
                ExchangeV2Event.Transition(
                    timestampMs = 100L,
                    from = ExchangeV2State.Bootstrapped,
                    to = ExchangeV2State.Negotiating,
                    trigger = Hello.fromJson("""{"type":"hello"}"""),
                    localTrigger = null,
                ),
            )

            val updated = awaitItem()
            assertEquals(1, updated.rows.size)
            assertTrue(updated.rows.single().summary.contains("Bootstrapped → Negotiating"))
            assertEquals("Negotiating", updated.currentStateLabel)
        }
    }

    @Test fun `MessageSent event labelled as Sent with type`() = runTest {
        val viewModel = newViewModel()
        viewModel.viewState().test {
            awaitItem()
            eventFlow.emit(
                ExchangeV2Event.MessageSent(
                    timestampMs = 0L,
                    message = ExchangeV2Message.RecoveryCodeRequest.create(name = "me", kind = "3party"),
                ),
            )
            val state = awaitItem()
            assertEquals(1, state.rows.size)
            assertTrue(state.rows.single().summary.startsWith("Sent recovery_code_request"))
        }
    }

    @Test fun `MessageRejected with SameAccount labelled SameAccountAbort`() = runTest {
        val viewModel = newViewModel()
        viewModel.viewState().test {
            awaitItem()
            eventFlow.emit(
                ExchangeV2Event.MessageRejected(
                    timestampMs = 0L,
                    message = ExchangeV2Message.RecoveryCodeAvailable.create(
                        userId = "shared",
                        name = "Peer",
                        kind = "3party",
                    ),
                    state = ExchangeV2State.Negotiating,
                    reason = RejectReason.SameAccount,
                ),
            )
            val state = awaitItem()
            assertTrue(state.rows.single().summary.startsWith("SameAccountAbort"))
        }
    }

    @Test fun `Joiner_JoinFailed alert is a failure that points at the event log`() = runTest {
        val viewModel = newViewModel()
        viewModel.terminals().test {
            eventFlow.emit(
                joinerTerminal(
                    ExchangeV2State.Joiner.JoinFailed,
                    LocalTrigger.JoinerJoinComplete(ExchangeV2Message.RecoveryCodeDone.Reason.LoginFailed),
                ),
            )

            val terminal = awaitItem()
            assertEquals("✗ Join failed (Joiner)", terminal.title)
            assertFalse(terminal.isSuccess)
            assertEquals("Check the event log for details.", terminal.message)
        }
    }

    @Test fun `Presenter at Joiner_Joining with no recovery code reports LoginFailed to the peer`() = runTest {
        whenever(runner.pairingRole).thenReturn(PairingRole.Presenter)
        val joinComplete = Job()
        joinComplete.complete()
        whenever(runner.localTrigger(any())).thenReturn(joinComplete)
        newViewModel()

        eventFlow.emit(
            ExchangeV2Event.Transition(
                timestampMs = 0L,
                from = ExchangeV2State.Joiner.Waiting,
                to = ExchangeV2State.Joiner.Joining,
                trigger = ExchangeV2Message.RecoveryCodeResponse.create(recoveryCode = ""),
                localTrigger = null,
            ),
        )

        verify(runner).localTrigger(LocalTrigger.JoinerJoinComplete(ExchangeV2Message.RecoveryCodeDone.Reason.LoginFailed))
        verify(dispatcher, never()).route(any())
    }

    @Test fun `Host_Done alert is a failure when the peer reported one`() = runTest {
        val viewModel = newViewModel()
        viewModel.terminals().test {
            eventFlow.emit(hostDone(ExchangeV2Message.RecoveryCodeDone.Reason.LoginFailed))

            val terminal = awaitItem()
            assertEquals("✗ Join failed on the peer", terminal.title)
            assertFalse(terminal.isSuccess)
        }
    }

    @Test fun `Host_Done alert is a success when the peer reported success`() = runTest {
        val viewModel = newViewModel()
        viewModel.terminals().test {
            eventFlow.emit(hostDone(ExchangeV2Message.RecoveryCodeDone.Reason.Success))

            val terminal = awaitItem()
            assertEquals("✓ Pairing complete (Host)", terminal.title)
            assertTrue(terminal.isSuccess)
        }
    }

    @Test fun `Host_Done alert is a success when a pre-2_1 peer sent no report`() = runTest {
        val viewModel = newViewModel()
        viewModel.terminals().test {
            eventFlow.emit(
                ExchangeV2Event.Transition(
                    timestampMs = 0L,
                    from = ExchangeV2State.Host.Sending,
                    to = ExchangeV2State.Host.Done,
                    trigger = null,
                    localTrigger = LocalTrigger.HostSendComplete(ExchangeProtocolVersion.V2_0),
                ),
            )

            val terminal = awaitItem()
            assertEquals("✓ Pairing complete (Host)", terminal.title)
            assertTrue(terminal.isSuccess)
        }
    }

    @Test fun `onClearLogClicked empties rows`() = runTest {
        val viewModel = newViewModel()
        viewModel.viewState().test {
            awaitItem()
            eventFlow.emit(
                ExchangeV2Event.Transition(
                    timestampMs = 0L,
                    from = ExchangeV2State.Bootstrapped,
                    to = ExchangeV2State.Negotiating,
                    trigger = Hello.fromJson("{}"),
                    localTrigger = null,
                ),
            )
            assertEquals(1, awaitItem().rows.size)

            viewModel.onClearLogClicked()
            assertEquals(0, awaitItem().rows.size)
        }
    }

    // ---- Lifecycle / control ----

    @Test fun `onCancelClicked delegates to runner cancel`() = runTest {
        val viewModel = newViewModel()
        viewModel.onCancelClicked()
        verify(runner).cancel()
    }

    // ---- Dispatch routing through SyncCodeDispatcher ----

    @Test fun `onRunScanClicked delegates to dispatcher route`() {
        val authCode = SyncAuthCode.Unknown("anything")
        whenever(dispatcher.route(any())).thenReturn(RouteDecision.Legacy(authCode))
        val viewModel = newViewModel()

        viewModel.onRunScanClicked("the-url")

        verify(dispatcher).route("the-url")
    }

    @Test fun `Legacy v1 Recovery — calls processCode and does NOT collect from a dispatcher Flow`() {
        val recovery = SyncAuthCode.Recovery(mock())
        whenever(dispatcher.route(any())).thenReturn(RouteDecision.Legacy(recovery))
        whenever(syncAccountRepository.processCode(eq(recovery), anyOrNull())).thenReturn(Result.Success(true))
        val viewModel = newViewModel()

        viewModel.onRunScanClicked("v1-recovery-url")

        verify(syncAccountRepository).processCode(eq(recovery), anyOrNull())
    }

    @Test fun `Legacy v1 Connect — calls processCode`() {
        val connect = SyncAuthCode.Connect(mock())
        whenever(dispatcher.route(any())).thenReturn(RouteDecision.Legacy(connect))
        whenever(syncAccountRepository.processCode(eq(connect), anyOrNull())).thenReturn(Result.Success(true))
        val viewModel = newViewModel()

        viewModel.onRunScanClicked("v1-connect-url")

        verify(syncAccountRepository).processCode(eq(connect), anyOrNull())
    }

    @Test fun `Legacy Unknown — does NOT call processCode (surfaces as user-facing toast)`() {
        whenever(dispatcher.route(any())).thenReturn(RouteDecision.Legacy(SyncAuthCode.Unknown("garbage")))
        val viewModel = newViewModel()

        viewModel.onRunScanClicked("garbage")

        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    @Test fun `V2InProgress — collects the outcomes Flow without calling parseSyncAuthCode`() {
        whenever(dispatcher.route(any())).thenReturn(
            RouteDecision.V2InProgress(
                codeType = SyncCodeType.RECOVERY,
                outcomes = flowOf(DispatchOutcome.LoggedIn(path = SetupPath.RECOVERY)),
            ),
        )
        val viewModel = newViewModel()

        viewModel.onRunScanClicked("v2-url")

        verify(syncAccountRepository, never()).parseSyncAuthCode(any())
        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    @Test fun `V2InProgress with Failed outcome — does NOT crash and does NOT fall back to legacy`() {
        whenever(dispatcher.route(any())).thenReturn(
            RouteDecision.V2InProgress(
                codeType = SyncCodeType.RECOVERY,
                outcomes = flowOf(DispatchOutcome.Failed("BE rejected")),
            ),
        )
        val viewModel = newViewModel()

        viewModel.onRunScanClicked("v2-url")

        verify(syncAccountRepository, never()).parseSyncAuthCode(any())
    }
}
