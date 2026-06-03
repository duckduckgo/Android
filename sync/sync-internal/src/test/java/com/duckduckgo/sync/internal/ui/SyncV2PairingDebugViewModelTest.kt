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
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.Hello
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Runner
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State
import com.duckduckgo.sync.impl.exchange.v2.RejectReason
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private fun newViewModel() = SyncV2PairingDebugViewModel(
        runner = runner,
        syncStore = syncStore,
        syncAccountRepository = syncAccountRepository,
        dispatcher = dispatcher,
        dispatchers = coroutineTestRule.testDispatcherProvider,
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
                    trigger = Hello("""{"type":"hello"}"""),
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
                    message = ExchangeV2Message.RecoveryCodeRequest(rawJson = "{}", name = "me", kind = "3party"),
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
                    message = ExchangeV2Message.RecoveryCodeAvailable(
                        rawJson = "{}",
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

    @Test fun `onClearLogClicked empties rows`() = runTest {
        val viewModel = newViewModel()
        viewModel.viewState().test {
            awaitItem()
            eventFlow.emit(
                ExchangeV2Event.Transition(
                    timestampMs = 0L,
                    from = ExchangeV2State.Bootstrapped,
                    to = ExchangeV2State.Negotiating,
                    trigger = Hello("{}"),
                    localTrigger = null,
                ),
            )
            assertEquals(1, awaitItem().rows.size)

            viewModel.onClearLogClicked()
            assertEquals(0, awaitItem().rows.size)
        }
    }

    // ---- Lifecycle / control ----

    @Test fun `onCancelClicked delegates to runner cancel`() {
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
            RouteDecision.V2InProgress(outcomes = flowOf(DispatchOutcome.LoggedIn)),
        )
        val viewModel = newViewModel()

        viewModel.onRunScanClicked("v2-url")

        verify(syncAccountRepository, never()).parseSyncAuthCode(any())
        verify(syncAccountRepository, never()).processCode(any(), anyOrNull())
    }

    @Test fun `V2InProgress with Failed outcome — does NOT crash and does NOT fall back to legacy`() {
        whenever(dispatcher.route(any())).thenReturn(
            RouteDecision.V2InProgress(outcomes = flowOf(DispatchOutcome.Failed("BE rejected"))),
        )
        val viewModel = newViewModel()

        viewModel.onRunScanClicked("v2-url")

        verify(syncAccountRepository, never()).parseSyncAuthCode(any())
    }
}
