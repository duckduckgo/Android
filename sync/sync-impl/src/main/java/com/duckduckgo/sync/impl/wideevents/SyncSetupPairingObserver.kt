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
import com.duckduckgo.sync.impl.exchange.v2.Role
import com.duckduckgo.sync.impl.exchange.v2.SessionErrorKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.ConnectMethod
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupFailureReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupRole
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupStep
import com.duckduckgo.sync.impl.pixels.SyncPixels.TimeoutStage

internal class SyncSetupPairingObserver(private val setupEvent: SyncSetupWideEvent) {
    private var joinerTimeoutStage: TimeoutStage? = null

    init {
        setupEvent.onPairingSessionReset()
    }

    fun onEvent(event: ExchangeV2Event) {
        when (event) {
            is ExchangeV2Event.VersionNegotiated -> {
                val version = when (event.negotiatedVersion) {
                    ExchangeProtocolVersion.V2_0 -> ExchangeProtocolVersion.V2_0
                    ExchangeProtocolVersion.V2_1 -> ExchangeProtocolVersion.V2_1
                    else -> return
                }
                setupEvent.onUpdate { it.copy(flowVersion = version) }
            }
            is ExchangeV2Event.RoleElected -> {
                joinerTimeoutStage = if (event.role == Role.Joiner) TimeoutStage.WAITING_FOR_CONFIRMATION else null
                val role = when (event.role) {
                    Role.Host -> SetupRole.HOST
                    Role.Joiner -> SetupRole.JOINER
                }
                val kind = event.peerKind.toPeerKind()
                val presenter = event.ownPairingRole == PairingRole.Presenter
                setupEvent.onUpdate {
                    it.copy(role = role, peerKind = kind, connectMethod = if (presenter) ConnectMethod.SHOW_CODE else it.connectMethod)
                }
            }
            is ExchangeV2Event.Transition -> {
                when {
                    event.from == ExchangeV2State.Bootstrapped && event.to == ExchangeV2State.Negotiating &&
                        event.trigger is ExchangeV2Message.Hello -> {
                        setupEvent.onUpdate { it.copy(connectMethod = ConnectMethod.SHOW_CODE) }
                        setupEvent.onStep(SetupStep.CODE_ACCEPTED_BY_OTHER_DEVICE)
                    }
                    event.to == ExchangeV2State.SameAccountAbort -> {
                        val kind = (event.trigger as? ExchangeV2Message.RecoveryCodeAvailable)?.kind.toPeerKind()
                        setupEvent.onUpdate { it.copy(peerKind = kind) }
                    }
                    event.from == ExchangeV2State.Joiner.Waiting && event.to == ExchangeV2State.Joiner.Waiting &&
                        event.trigger is ExchangeV2Message.RecoveryCodeConfirmed -> {
                        joinerTimeoutStage = TimeoutStage.WAITING_FOR_RECOVERY_CODE
                        setupEvent.onStep(SetupStep.PREPARING_SYNC)
                    }
                    event.to == ExchangeV2State.Joiner.Confirming || event.to == ExchangeV2State.Host.Confirming ->
                        setupEvent.onStep(SetupStep.CONFIRMATION_SCREEN)
                    event.localTrigger == LocalTrigger.UserConfirmedJoiner -> {
                        setupEvent.onStep(SetupStep.SYNC_CONFIRMATION)
                        setupEvent.onStep(SetupStep.WAITING_FOR_CONFIRMATION)
                    }
                    event.localTrigger == LocalTrigger.UserConfirmedHost -> {
                        setupEvent.onStep(SetupStep.SYNC_CONFIRMATION)
                        setupEvent.onStep(SetupStep.PREPARING_SYNC)
                    }
                    event.to == ExchangeV2State.Joiner.Joining && event.from != event.to -> {
                        joinerTimeoutStage = TimeoutStage.LOGGING_IN
                        setupEvent.onStep(SetupStep.PREPARING_SYNC)
                    }
                }
            }
            is ExchangeV2Event.SessionError -> when (event.kind) {
                SessionErrorKind.SessionTimeout -> {
                    val stage = if (event.timeoutStage == TimeoutStage.WAITING_FOR_RECOVERY_CODE) {
                        joinerTimeoutStage ?: event.timeoutStage
                    } else {
                        event.timeoutStage
                    }
                    setupEvent.onFailed(SetupFailureReason.SESSION_TIMEOUT, stage)
                }
                SessionErrorKind.PeerProtocolTooNew -> setupEvent.onFailed(SetupFailureReason.NEEDS_UPGRADE)
                else -> Unit
            }
            else -> Unit
        }
    }

    private fun String?.toPeerKind(): PeerKind? = when (this) {
        "ddg" -> PeerKind.DDG
        "3party" -> PeerKind.THIRD_PARTY
        else -> null
    }
}
