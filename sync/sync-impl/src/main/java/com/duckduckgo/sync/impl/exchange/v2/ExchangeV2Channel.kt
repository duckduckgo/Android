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

package com.duckduckgo.sync.impl.exchange.v2

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.ExchangeEnvelope
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncApi
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

/**
 * Wraps the BE relay endpoints with envelope encryption/decryption + a polling Flow.
 *
 * Spec: Transport TD (Asana 1214486492252757) §BE Relay + §Polling for messages.
 */
/** Own channel is gone on the relay (HTTP 404/410 on poll). Peer or BE TTL deleted it. */
class ChannelGone(val status: Int) : RuntimeException("Poll got $status — channel gone")

/** Malformed poll request (HTTP 400). Client-side protocol bug. */
class PollBadRequest(val status: Int) : RuntimeException("Poll rejected with $status — malformed request")

/** Poll auth/policy failure (HTTP 401/403). Credentials invalidated or server-side policy denied. */
class PollAuthDenied(val status: Int) : RuntimeException("Poll denied with $status — auth or policy")

interface ExchangeV2Channel {

    /**
     * Create [channelId] on the relay. Returns Success on 2xx. Returns Error with code=409 on
     * UUID collision (caller should retry with a fresh UUID).
     */
    fun createChannel(channelId: String): Result<Unit>

    /**
     * Encrypt [messageJson] and send it to [peerChannelId]. Sender's kid is [ownChannelId].
     */
    fun sendMessage(
        messageJson: String,
        peerChannelId: String,
        peerPublicKeyBase64: String,
        ownChannelId: String,
    ): Result<Unit>

    /**
     * Poll loop on [ownChannelId], emitting decrypted + parsed messages.
     *  - Transient statuses (5xx / 429 / network / timeouts) keep polling; the 5-min session timer catches persistent transient failures.
     *  - Throws [ChannelGone] on 404/410 (channel deleted or TTL'd), [PollBadRequest] on 400, [PollAuthDenied] on 401/403
     *  - Throws [EnvelopeVersionTooNew] on a too-new envelope and [EnvelopeDecryptFailure] on decrypt failure
     */
    fun poll(ownChannelId: String, ownPrivateKeyBase64: String): Flow<ExchangeV2Message>

    /** Best-effort DELETE of our own channel. Used on user-cancel + terminal SM states. */
    fun deleteChannel(channelId: String): Result<Unit>
}

@ContributesBinding(AppScope::class)
class RealExchangeV2Channel @Inject constructor(
    private val syncApi: SyncApi,
    private val envelope: ExchangeV2Envelope,
    private val messageParser: ExchangeV2MessageParser,
) : ExchangeV2Channel {

    override fun createChannel(channelId: String): Result<Unit> {
        logcat { "Sync-ExchangeV2: PUT /sync/v2/exchange/$channelId" }
        return syncApi.createExchangeChannel(channelId)
    }

    override fun sendMessage(
        messageJson: String,
        peerChannelId: String,
        peerPublicKeyBase64: String,
        ownChannelId: String,
    ): Result<Unit> {
        val sealed = runCatching {
            envelope.seal(messageJson, peerPublicKeyBase64, ownChannelId)
        }.getOrElse {
            logcat(ERROR) { "Sync-ExchangeV2: seal failed: ${it.message}" }
            return Result.Error(reason = "Failed to seal envelope: ${it.message}")
        }
        logcat { "Sync-ExchangeV2: POST /sync/v2/exchange/$peerChannelId/messages" }
        return syncApi.sendExchangeMessages(peerChannelId, listOf(sealed))
    }

    override fun poll(ownChannelId: String, ownPrivateKeyBase64: String): Flow<ExchangeV2Message> = flow {
        var cursor = 0
        while (true) {
            when (val outcome = syncApi.pollExchangeMessages(ownChannelId, cursor)) {
                is Result.Success -> {
                    for (entry in outcome.data) {
                        val decoded = decode(entry, ownPrivateKeyBase64)
                        cursor = entry.seq
                        emit(decoded)
                    }
                }
                is Result.Error -> {
                    when (outcome.code) {
                        404, 410 -> throw ChannelGone(outcome.code)
                        400 -> throw PollBadRequest(outcome.code)
                        401, 403 -> throw PollAuthDenied(outcome.code)
                        else -> logcat(ERROR) { "Sync-ExchangeV2: transient poll error ${outcome.code}: ${outcome.reason}, retrying" }
                    }
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    override fun deleteChannel(channelId: String): Result<Unit> {
        logcat { "Sync-ExchangeV2: DELETE /sync/v2/exchange/$channelId (best effort)" }
        return syncApi.deleteExchangeChannel(channelId)
    }

    private fun decode(entry: com.duckduckgo.sync.impl.ExchangeMessageEntry, ownPrivateKeyBase64: String): ExchangeV2Message {
        val inner = runCatching {
            envelope.open(ExchangeEnvelope(entry.version, entry.payload), ownPrivateKeyBase64)
        }.getOrElse {
            if (it is EnvelopeVersionTooNew) throw it
            // Permanent: the same bytes fail the same way every poll, so surface as terminal.
            logcat(ERROR) { "Sync-ExchangeV2: envelope open failed seq=${entry.seq}: ${it.message}" }
            throw EnvelopeDecryptFailure(entry.seq, it)
        }
        return messageParser.parse(inner)
    }

    companion object {
        private const val POLL_INTERVAL_MS: Long = 1_000L
    }
}
