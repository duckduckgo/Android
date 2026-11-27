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

package com.duckduckgo.sync.impl.wideevents

import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State
import com.duckduckgo.sync.impl.exchange.v2.LocalTrigger
import com.duckduckgo.sync.impl.exchange.v2.PairingRole
import com.duckduckgo.sync.impl.exchange.v2.PeerVersionSource
import com.duckduckgo.sync.impl.exchange.v2.Role
import com.duckduckgo.sync.impl.exchange.v2.SessionErrorKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.ConnectMethod
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupFailureReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupMode
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupRole
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupStep
import com.duckduckgo.sync.impl.pixels.SyncPixels.TimeoutStage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SyncSetupPairingObserverTest {
    private val setupEvent = mock<SyncSetupWideEvent>()
    private var fields = SetupFields(
        setupMode = SetupMode.MULTI_DEVICE,
        flowVersion = ExchangeProtocolVersion.V2_1,
        connectMethod = ConnectMethod.PASTE_CODE,
    )
    private val observer = SyncSetupPairingObserver(setupEvent).also {
        doAnswer { invocation ->
            fields = invocation.getArgument<(SetupFields) -> SetupFields>(0)(fields)
        }.whenever(setupEvent).onUpdate(any())
    }

    @Test fun `new collection clears previous session metadata`() {
        verify(setupEvent).onPairingSessionReset()
    }

    @Test fun `negotiation and election record bounded version role peer and method`() {
        for (role in listOf(Role.Host, Role.Joiner)) {
            for (pairingRole in listOf(PairingRole.Presenter, PairingRole.Scanner)) {
                for ((kind, expected) in listOf("ddg" to PeerKind.DDG, "3party" to PeerKind.THIRD_PARTY, "unknown" to null)) {
                    fields = fields.copy(connectMethod = ConnectMethod.PASTE_CODE)
                    observer.onEvent(
                        ExchangeV2Event.VersionNegotiated(
                            1,
                            PeerVersionSource.LinkingCode,
                            ExchangeProtocolVersion.V2_0,
                            ExchangeProtocolVersion.V2_1,
                            ExchangeProtocolVersion.V2_0,
                        ),
                    )
                    observer.onEvent(election(role, pairingRole, kind))
                    assertEquals(ExchangeProtocolVersion.V2_0, fields.flowVersion)
                    assertEquals(if (role == Role.Host) SetupRole.HOST else SetupRole.JOINER, fields.role)
                    assertEquals(expected, fields.peerKind)
                    assertEquals(
                        if (pairingRole == PairingRole.Presenter) ConnectMethod.SHOW_CODE else ConnectMethod.PASTE_CODE,
                        fields.connectMethod,
                    )
                }
            }
        }
    }

    @Test fun `same account records known peer and preserves scanner method without electing role`() {
        observer.onEvent(
            transition(
                ExchangeV2State.Negotiating,
                ExchangeV2State.SameAccountAbort,
                ExchangeV2Message.RecoveryCodeAvailable.create("code", "Peer", "ddg"),
            ),
        )
        assertEquals(PeerKind.DDG, fields.peerKind)
        assertEquals(null, fields.role)
        assertEquals(ConnectMethod.PASTE_CODE, fields.connectMethod)
    }

    @Test fun `presenter hello records displayed code before same account abort`() {
        observer.onEvent(transition(ExchangeV2State.Bootstrapped, ExchangeV2State.Negotiating, ExchangeV2Message.Hello.fromJson("{}")))
        observer.onEvent(
            transition(
                ExchangeV2State.Negotiating,
                ExchangeV2State.SameAccountAbort,
                ExchangeV2Message.RecoveryCodeAvailable.create("code", "Peer", "3party"),
            ),
        )
        assertEquals(PeerKind.THIRD_PARTY, fields.peerKind)
        assertEquals(null, fields.role)
        assertEquals(ConnectMethod.SHOW_CODE, fields.connectMethod)
        verify(setupEvent).onStep(SetupStep.CODE_ACCEPTED_BY_OTHER_DEVICE)
    }

    @Test fun `confirmation and accepted host confirmation record preparation steps in order`() {
        observer.onEvent(transition(ExchangeV2State.Negotiating, ExchangeV2State.Joiner.Confirming))
        observer.onEvent(transition(ExchangeV2State.Joiner.Confirming, ExchangeV2State.Joiner.Waiting, local = LocalTrigger.UserConfirmedJoiner))
        observer.onEvent(acceptedConfirmation())
        inOrder(setupEvent) {
            verify(setupEvent).onStep(SetupStep.CONFIRMATION_SCREEN)
            verify(setupEvent).onStep(SetupStep.SYNC_CONFIRMATION)
            verify(setupEvent).onStep(SetupStep.WAITING_FOR_CONFIRMATION)
            verify(setupEvent).onStep(SetupStep.PREPARING_SYNC)
        }
    }

    @Test fun `host confirmation records preparation`() {
        observer.onEvent(transition(ExchangeV2State.Negotiating, ExchangeV2State.Host.Confirming))
        observer.onEvent(transition(ExchangeV2State.Host.Confirming, ExchangeV2State.Host.Sending, local = LocalTrigger.UserConfirmedHost))
        inOrder(setupEvent) {
            verify(setupEvent).onStep(SetupStep.CONFIRMATION_SCREEN)
            verify(setupEvent).onStep(SetupStep.SYNC_CONFIRMATION)
            verify(setupEvent).onStep(SetupStep.PREPARING_SYNC)
        }
    }

    @Test fun `joiner recovery timeout before host confirmation is a human wait`() {
        observer.onEvent(election(Role.Joiner))
        observer.onEvent(timeout(TimeoutStage.WAITING_FOR_RECOVERY_CODE))
        verify(setupEvent).onFailed(SetupFailureReason.SESSION_TIMEOUT, TimeoutStage.WAITING_FOR_CONFIRMATION)
    }

    @Test fun `received but unaccepted host confirmation does not change human wait`() {
        observer.onEvent(election(Role.Joiner))
        observer.onEvent(ExchangeV2Event.MessageReceived(1, ExchangeV2Message.RecoveryCodeConfirmed.create()))
        observer.onEvent(timeout(TimeoutStage.WAITING_FOR_RECOVERY_CODE))
        verify(setupEvent).onFailed(SetupFailureReason.SESSION_TIMEOUT, TimeoutStage.WAITING_FOR_CONFIRMATION)
    }

    @Test fun `joiner recovery timeout after accepted host confirmation is a software wait`() {
        observer.onEvent(election(Role.Joiner))
        observer.onEvent(acceptedConfirmation())
        observer.onEvent(timeout(TimeoutStage.WAITING_FOR_RECOVERY_CODE))
        verify(setupEvent).onFailed(SetupFailureReason.SESSION_TIMEOUT, TimeoutStage.WAITING_FOR_RECOVERY_CODE)
    }

    @Test fun `joining starts preparation and retains login timeout`() {
        observer.onEvent(election(Role.Joiner))
        observer.onEvent(transition(ExchangeV2State.Joiner.Waiting, ExchangeV2State.Joiner.Joining))
        observer.onEvent(timeout(TimeoutStage.LOGGING_IN))
        verify(setupEvent).onStep(SetupStep.PREPARING_SYNC)
        verify(setupEvent).onFailed(SetupFailureReason.SESSION_TIMEOUT, TimeoutStage.LOGGING_IN)
    }

    @Test fun `other timeout stages pass through unchanged`() {
        for (stage in TimeoutStage.entries + null) {
            if (stage == TimeoutStage.WAITING_FOR_RECOVERY_CODE) continue
            observer.onEvent(election(Role.Joiner))
            observer.onEvent(timeout(stage))
            verify(setupEvent).onFailed(SetupFailureReason.SESSION_TIMEOUT, stage)
        }
    }

    @Test fun `host recovery timeout remains a software wait`() {
        observer.onEvent(election(Role.Host))
        observer.onEvent(timeout(TimeoutStage.WAITING_FOR_RECOVERY_CODE))
        verify(setupEvent).onFailed(SetupFailureReason.SESSION_TIMEOUT, TimeoutStage.WAITING_FOR_RECOVERY_CODE)
    }

    @Test fun `structured too new protocol reports upgrade without inspecting error text`() {
        observer.onEvent(ExchangeV2Event.SessionError(1, "opaque", SessionErrorKind.PeerProtocolTooNew))
        verify(setupEvent).onFailed(SetupFailureReason.NEEDS_UPGRADE)
    }

    @Test fun `irrelevant events have no telemetry effect`() {
        clearInvocations(setupEvent)
        observer.onEvent(ExchangeV2Event.SessionStarted(1, PairingRole.Scanner, "channel", null))
        observer.onEvent(ExchangeV2Event.SessionError(1, "opaque", SessionErrorKind.Unknown))
        observer.onEvent(transition(ExchangeV2State.Bootstrapped, ExchangeV2State.Bootstrapped))
        verifyNoInteractions(setupEvent)
    }

    private fun election(role: Role, pairingRole: PairingRole = PairingRole.Scanner, kind: String = "ddg") =
        ExchangeV2Event.RoleElected(1, role, pairingRole, false, "ddg", kind, true)

    private fun timeout(stage: TimeoutStage?) = ExchangeV2Event.SessionError(1, "opaque", SessionErrorKind.SessionTimeout, stage)

    private fun acceptedConfirmation() = transition(
        ExchangeV2State.Joiner.Waiting,
        ExchangeV2State.Joiner.Waiting,
        ExchangeV2Message.RecoveryCodeConfirmed.create(),
    )

    private fun transition(
        from: ExchangeV2State,
        to: ExchangeV2State,
        message: ExchangeV2Message? = null,
        local: LocalTrigger? = null,
    ) = ExchangeV2Event.Transition(1, from, to, message, local)
}
