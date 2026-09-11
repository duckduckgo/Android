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

import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeDone
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Runner
import com.duckduckgo.sync.impl.exchange.v2.PairingRole
import com.duckduckgo.sync.impl.exchange.v2.PeerVersionSource
import com.duckduckgo.sync.impl.exchange.v2.Role
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ExchangeV2RecoverCodeDonePixelObserverTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val runnerEvents = MutableSharedFlow<ExchangeV2Event>()
    private val runner: ExchangeV2Runner = mock()
    private val syncPixels: SyncPixels = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private val testee = ExchangeV2RecoverCodeDonePixelObserver(
        appCoroutineScope = coroutineTestRule.testScope,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        runner = runner,
        syncPixels = syncPixels,
    )

    @Before
    fun setup() {
        whenever(runner.events).thenReturn(runnerEvents)
        testee.onCreate(lifecycleOwner)
    }

    @Test
    fun `recovery_code_done with Success sent - fires success pixel`() = runTest {
        runnerEvents.emit(roleElected(ownSignedIn = false, peerKind = "3party", peerSignedIn = true))
        runnerEvents.emit(versionNegotiated(ExchangeProtocolVersion.V2_1))

        runnerEvents.emit(messageSent(RecoveryCodeDone.create(RecoveryCodeDone.Reason.Success)))

        verify(syncPixels).fireSyncSetupJoinerSuccess(
            hostHasAccount = true,
            hostKind = PeerKind.THIRD_PARTY,
            joinerHasAccount = false,
            joinerKind = PeerKind.DDG,
            negotiatedVersion = ExchangeProtocolVersion.V2_1,
        )
    }

    @Test
    fun `recovery_code_done with LoginFailed sent - fires failure pixel`() = runTest {
        runnerEvents.emit(roleElected(ownSignedIn = true, peerKind = "ddg", peerSignedIn = false))
        runnerEvents.emit(versionNegotiated(ExchangeProtocolVersion.V2_1))

        runnerEvents.emit(messageSent(RecoveryCodeDone.create(RecoveryCodeDone.Reason.LoginFailed)))

        verify(syncPixels).fireSyncSetupJoinerFailure(
            hostHasAccount = false,
            hostKind = PeerKind.DDG,
            joinerHasAccount = true,
            joinerKind = PeerKind.DDG,
            negotiatedVersion = ExchangeProtocolVersion.V2_1,
        )
    }

    @Test
    fun `recovery_code_done with ScopeRejected sent - fires failure pixel`() = runTest {
        runnerEvents.emit(roleElected(ownSignedIn = false, peerKind = "ddg", peerSignedIn = true))
        runnerEvents.emit(versionNegotiated(ExchangeProtocolVersion.V2_1))

        runnerEvents.emit(messageSent(RecoveryCodeDone.create(RecoveryCodeDone.Reason.ScopeRejected)))

        verify(syncPixels).fireSyncSetupJoinerFailure(
            hostHasAccount = true,
            hostKind = PeerKind.DDG,
            joinerHasAccount = false,
            joinerKind = PeerKind.DDG,
            negotiatedVersion = ExchangeProtocolVersion.V2_1,
        )
    }

    @Test
    fun `recovery_code_done with Unknown reason sent - fires failure pixel`() = runTest {
        runnerEvents.emit(roleElected(ownSignedIn = false, peerKind = "ddg", peerSignedIn = true))
        runnerEvents.emit(versionNegotiated(ExchangeProtocolVersion.V2_1))

        runnerEvents.emit(messageSent(RecoveryCodeDone.create(RecoveryCodeDone.Reason.Unknown("weird"))))

        verify(syncPixels).fireSyncSetupJoinerFailure(
            hostHasAccount = true,
            hostKind = PeerKind.DDG,
            joinerHasAccount = false,
            joinerKind = PeerKind.DDG,
            negotiatedVersion = ExchangeProtocolVersion.V2_1,
        )
    }

    @Test
    fun `recovery_code_done sent without role election - no pixel fired`() = runTest {
        runnerEvents.emit(versionNegotiated(ExchangeProtocolVersion.V2_1))

        runnerEvents.emit(messageSent(RecoveryCodeDone.create(RecoveryCodeDone.Reason.Success)))

        verifyNoInteractions(syncPixels)
    }

    @Test
    fun `recovery_code_done sent without version negotiation - no pixel fired`() = runTest {
        runnerEvents.emit(roleElected(ownSignedIn = false, peerKind = "ddg", peerSignedIn = true))

        runnerEvents.emit(messageSent(RecoveryCodeDone.create(RecoveryCodeDone.Reason.Success)))

        verifyNoInteractions(syncPixels)
    }

    @Test
    fun `other message types sent - no pixel fired`() = runTest {
        runnerEvents.emit(roleElected(ownSignedIn = false, peerKind = "ddg", peerSignedIn = true))
        runnerEvents.emit(versionNegotiated(ExchangeProtocolVersion.V2_1))

        runnerEvents.emit(messageSent(ExchangeV2Message.Bye.create(ExchangeV2Message.Bye.Reason.Done)))

        verifyNoInteractions(syncPixels)
    }

    @Test
    fun `recovery_code_done received rather than sent - no pixel fired`() = runTest {
        runnerEvents.emit(roleElected(ownSignedIn = true, peerKind = "ddg", peerSignedIn = false))
        runnerEvents.emit(versionNegotiated(ExchangeProtocolVersion.V2_1))

        runnerEvents.emit(
            ExchangeV2Event.MessageReceived(
                timestampMs = TIMESTAMP_MS,
                message = RecoveryCodeDone.create(RecoveryCodeDone.Reason.Success),
            ),
        )

        verifyNoInteractions(syncPixels)
    }

    private fun roleElected(
        ownSignedIn: Boolean,
        peerKind: String?,
        peerSignedIn: Boolean,
    ): ExchangeV2Event.RoleElected {
        return ExchangeV2Event.RoleElected(
            timestampMs = TIMESTAMP_MS,
            role = Role.Joiner,
            ownPairingRole = PairingRole.Scanner,
            ownSignedIn = ownSignedIn,
            ownKind = "ddg",
            peerKind = peerKind,
            peerSignedIn = peerSignedIn,
        )
    }

    private fun versionNegotiated(negotiatedVersion: ExchangeProtocolVersion): ExchangeV2Event.VersionNegotiated {
        return ExchangeV2Event.VersionNegotiated(
            timestampMs = TIMESTAMP_MS,
            peerSource = PeerVersionSource.LinkingCode,
            peerVersion = negotiatedVersion,
            ourVersion = ExchangeProtocolVersion.V2_1,
            negotiatedVersion = negotiatedVersion,
        )
    }

    private fun messageSent(message: ExchangeV2Message): ExchangeV2Event.MessageSent {
        return ExchangeV2Event.MessageSent(timestampMs = TIMESTAMP_MS, message = message)
    }

    companion object {
        private const val TIMESTAMP_MS = 1_000L
    }
}
