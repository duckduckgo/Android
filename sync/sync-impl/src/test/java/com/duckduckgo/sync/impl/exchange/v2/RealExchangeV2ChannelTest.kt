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

import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
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

    @Test fun `poll stops without retrying when the relay returns a non-recoverable status`() = runTest {
        whenever(syncApi.pollExchangeMessages(any(), any())).thenReturn(
            Result.Error(code = 403, reason = "Forbidden"),
            Result.Error(code = 404, reason = "should not be reached"),
        )

        val received = channel.poll("own-channel", "own-priv").toList()

        assertTrue(received.isEmpty())
        verify(syncApi, times(1)).pollExchangeMessages(any(), any())
    }

    @Test fun `poll keeps polling after a transient status and ends on 404`() = runTest {
        whenever(syncApi.pollExchangeMessages(any(), any())).thenReturn(
            Result.Error(code = 500, reason = "Server error"),
            Result.Error(code = 404, reason = "channel gone"),
        )

        val received = channel.poll("own-channel", "own-priv").toList()

        assertTrue(received.isEmpty())
        verify(syncApi, times(2)).pollExchangeMessages(any(), any())
    }
}
