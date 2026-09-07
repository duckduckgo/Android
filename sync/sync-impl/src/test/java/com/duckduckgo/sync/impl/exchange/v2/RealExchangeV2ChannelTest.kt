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

import com.duckduckgo.sync.impl.ExchangeEnvelope
import com.duckduckgo.sync.impl.ExchangeMessageEntry
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealExchangeV2ChannelTest {

    private val syncApi: SyncApi = mock()
    private val envelope: ExchangeV2Envelope = mock()
    private val messageParser: ExchangeV2MessageParser = mock()

    private val channel = RealExchangeV2Channel(
        syncApi = syncApi,
        envelope = envelope,
        messageParser = messageParser,
    )

    @Test fun `poll throws PollAuthDenied on 403 without retrying`() = runTest {
        whenever(syncApi.pollExchangeMessages(any(), any(), any())).thenReturn(
            Result.Error(code = 403, reason = "Forbidden"),
            Result.Error(code = 404, reason = "should not be reached"),
        )

        var thrown: PollAuthDenied? = null
        try { channel.poll("own-channel", "own-priv", "own-secret").toList() } catch (e: PollAuthDenied) { thrown = e }

        assertNotNull(thrown)
        assertEquals(403, thrown!!.status)
        verify(syncApi, times(1)).pollExchangeMessages(any(), any(), any())
    }

    @Test fun `poll throws PollBadRequest on 400 without retrying`() = runTest {
        whenever(syncApi.pollExchangeMessages(any(), any(), any())).thenReturn(
            Result.Error(code = 400, reason = "Bad Request"),
        )

        var thrown: PollBadRequest? = null
        try { channel.poll("own-channel", "own-priv", "own-secret").toList() } catch (e: PollBadRequest) { thrown = e }

        assertNotNull(thrown)
        assertEquals(400, thrown!!.status)
    }

    @Test fun `poll keeps polling after a transient status and throws ChannelGone on 404`() = runTest {
        whenever(syncApi.pollExchangeMessages(any(), any(), any())).thenReturn(
            Result.Error(code = 500, reason = "Server error"),
            Result.Error(code = 404, reason = "channel gone"),
        )

        var thrown: ChannelGone? = null
        try { channel.poll("own-channel", "own-priv", "own-secret").toList() } catch (e: ChannelGone) { thrown = e }

        assertNotNull(thrown)
        assertEquals(404, thrown!!.status)
        verify(syncApi, times(2)).pollExchangeMessages(any(), any(), any())
    }

    @Test fun `poll throws ChannelGone on 410`() = runTest {
        whenever(syncApi.pollExchangeMessages(any(), any(), any())).thenReturn(
            Result.Error(code = 410, reason = "Gone"),
        )

        var thrown: ChannelGone? = null
        try { channel.poll("own-channel", "own-priv", "own-secret").toList() } catch (e: ChannelGone) { thrown = e }

        assertNotNull(thrown)
        assertEquals(410, thrown!!.status)
    }

    @Test fun `createChannel claims the channel with the given secret`() = runTest {
        whenever(syncApi.createExchangeChannel(any(), any())).thenReturn(Result.Success(Unit))

        channel.createChannel("own-channel", "own-secret")

        verify(syncApi).createExchangeChannel(channelId = "own-channel", channelSecret = "own-secret")
    }

    @Test fun `sendMessage authorizes the write to the peer channel with our own secret`() = runTest {
        val sealed = ExchangeEnvelope(version = "2.0", payload = "jwe")
        whenever(envelope.seal(any(), any(), any())).thenReturn(sealed)
        whenever(syncApi.sendExchangeMessages(any(), any(), any())).thenReturn(Result.Success(Unit))

        channel.sendMessage(
            message = ExchangeV2Message.Hello.fromJson("{}"),
            peerChannelId = "peer-channel",
            peerPublicKeyBase64 = "peer-pub",
            ownChannelId = "own-channel",
            ownChannelSecret = "own-secret",
        )

        verify(syncApi).sendExchangeMessages(
            channelId = "peer-channel",
            channelSecret = "own-secret",
            envelopes = listOf(sealed),
        )
    }

    @Test fun `poll authorizes each request with the own channel secret`() = runTest {
        whenever(syncApi.pollExchangeMessages(any(), any(), any())).thenReturn(
            Result.Success(listOf(ExchangeMessageEntry(seq = 1, version = "2.0", payload = "jwe"))),
            Result.Error(code = 410, reason = "Gone"),
        )
        whenever(envelope.open(any(), any())).thenReturn("{}")
        whenever(messageParser.parse(any())).thenReturn(ExchangeV2Message.Hello.fromJson("{}"))

        runCatching { channel.poll("own-channel", "own-priv", "own-secret").toList() }

        verify(syncApi).pollExchangeMessages(channelId = "own-channel", channelSecret = "own-secret", after = 0)
        verify(syncApi).pollExchangeMessages(channelId = "own-channel", channelSecret = "own-secret", after = 1)
    }

    @Test fun `deleteChannel authorizes the delete with the channel secret`() = runTest {
        whenever(syncApi.deleteExchangeChannel(any(), any())).thenReturn(Result.Success(Unit))

        channel.deleteChannel("own-channel", "own-secret")

        verify(syncApi).deleteExchangeChannel(channelId = "own-channel", channelSecret = "own-secret")
    }

    @Test fun `a channel without a secret is created, polled and deleted unauthenticated`() = runTest {
        whenever(syncApi.createExchangeChannel(any(), anyOrNull())).thenReturn(Result.Success(Unit))
        whenever(syncApi.pollExchangeMessages(any(), anyOrNull(), any())).thenReturn(Result.Error(code = 410, reason = "Gone"))
        whenever(syncApi.deleteExchangeChannel(any(), anyOrNull())).thenReturn(Result.Success(Unit))

        channel.createChannel("own-channel", null)
        runCatching { channel.poll("own-channel", "own-priv", null).toList() }
        channel.deleteChannel("own-channel", null)

        verify(syncApi).createExchangeChannel(channelId = "own-channel", channelSecret = null)
        verify(syncApi).pollExchangeMessages(channelId = "own-channel", channelSecret = null, after = 0)
        verify(syncApi).deleteExchangeChannel(channelId = "own-channel", channelSecret = null)
    }
}
