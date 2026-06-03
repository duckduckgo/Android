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
     * Long-running poll loop on [ownChannelId]. Emits decrypted + parsed messages as they
     * arrive. The flow:
     *  - Polls every 1s using cursor-as-ack semantics.
     *  - Completes (returns) on 404 (peer/server closed the channel) or [PollOutcome.Closed].
     *  - Throws [EnvelopeVersionTooNew] if an envelope arrives with a higher major version.
     *  - Drops envelopes that fail to decrypt or that have unknown types (forward-compat).
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
                    if (outcome.code == HTTP_NOT_FOUND) {
                        logcat { "Sync-ExchangeV2: channel $ownChannelId gone (404), ending poll" }
                        return@flow
                    }
                    logcat(ERROR) { "Sync-ExchangeV2: poll error ${outcome.code}: ${outcome.reason}" }
                    // Transient error — back off but stay in the loop.
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
            // Permanent: the same bytes will fail the same way every poll. Surface as terminal
            // so the runner can stop the loop and emit a SessionError instead of spamming logs.
            logcat(ERROR) { "Sync-ExchangeV2: envelope open failed seq=${entry.seq}: ${it.message}" }
            throw EnvelopeDecryptFailure(entry.seq, it)
        }
        return messageParser.parse(inner)
    }

    companion object {
        private const val POLL_INTERVAL_MS: Long = 1_000L
        private const val HTTP_NOT_FOUND = 404
    }
}
