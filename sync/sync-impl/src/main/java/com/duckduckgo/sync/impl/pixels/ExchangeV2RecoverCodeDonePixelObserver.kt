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
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message.RecoveryCodeDone
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Runner
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class ExchangeV2RecoverCodeDonePixelObserver @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val runner: ExchangeV2Runner,
    private val syncPixels: SyncPixels,
) : MainProcessLifecycleObserver {

    // Never cleared, only overwritten. A session can only send recovery_code_done after emitting
    // its own ExchangeV2Event.RoleElected and ExchangeV2Event.VersionNegotiated, so the context
    // is always fresh when read.
    private var sessionContext = SessionContext()

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            runner.events.collect(::handleEvent)
        }
    }

    private fun handleEvent(event: ExchangeV2Event) {
        when (event) {
            is ExchangeV2Event.RoleElected -> {
                sessionContext = sessionContext.copy(
                    ownSignedIn = event.ownSignedIn,
                    ownKind = event.ownKind.toPeerKind(),
                    peerSignedIn = event.peerSignedIn,
                    peerKind = event.peerKind.toPeerKind(),
                )
            }

            is ExchangeV2Event.VersionNegotiated -> {
                sessionContext = sessionContext.copy(
                    negotiatedVersion = event.negotiatedVersion,
                )
            }

            is ExchangeV2Event.MessageSent -> {
                val message = event.message
                if (message is RecoveryCodeDone) {
                    fireJoinerReport(message.reason)
                }
            }

            else -> Unit
        }
    }

    private fun fireJoinerReport(reason: RecoveryCodeDone.Reason) {
        val context = sessionContext
        val joinerKind = context.ownKind
        val hostKind = context.peerKind
        val negotiatedVersion = context.negotiatedVersion
        if (joinerKind == null || hostKind == null || negotiatedVersion == null) {
            logcat { "$TAG: recovery_code_done sent without full session context; skipping joiner report pixel" }
            return
        }

        if (reason == RecoveryCodeDone.Reason.Success) {
            syncPixels.fireSyncSetupJoinerSuccess(
                hostHasAccount = context.peerSignedIn,
                hostKind = hostKind,
                joinerHasAccount = context.ownSignedIn,
                joinerKind = joinerKind,
                negotiatedVersion = negotiatedVersion,
            )
        } else {
            syncPixels.fireSyncSetupJoinerFailure(
                hostHasAccount = context.peerSignedIn,
                hostKind = hostKind,
                joinerHasAccount = context.ownSignedIn,
                joinerKind = joinerKind,
                negotiatedVersion = negotiatedVersion,
            )
        }
    }

    private fun String?.toPeerKind(): PeerKind? = when (this) {
        PeerKind.DDG.value -> PeerKind.DDG
        PeerKind.THIRD_PARTY.value -> PeerKind.THIRD_PARTY
        else -> null
    }

    private data class SessionContext(
        val ownSignedIn: Boolean = false,
        val ownKind: PeerKind? = null,
        val peerSignedIn: Boolean = false,
        val peerKind: PeerKind? = null,
        val negotiatedVersion: ExchangeProtocolVersion? = null,
    )

    companion object {
        private const val TAG = "Sync-ExchangeV2"
    }
}
