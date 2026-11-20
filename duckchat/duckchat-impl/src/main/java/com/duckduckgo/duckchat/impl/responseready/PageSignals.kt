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

package com.duckduckgo.duckchat.impl.responseready

import com.duckduckgo.duckchat.impl.pendingresponse.PendingResponseStatus
import com.duckduckgo.duckchat.impl.pendingresponse.PendingResponseStatus.Blocked
import com.duckduckgo.duckchat.impl.pendingresponse.PendingResponseStatus.Error
import com.duckduckgo.duckchat.impl.pendingresponse.PendingResponseStatus.Ready
import com.duckduckgo.duckchat.impl.pendingresponse.PendingResponseStatus.StartStream
import com.duckduckgo.duckchat.impl.pendingresponse.PendingResponseStatus.Streaming

internal enum class PageSignal { READY, START_ERROR_SETTLE, CANCEL_ERROR_SETTLE, NONE }

/** Reads what the page's statuses, received while the user is away, mean for a left response. */
internal class PageSignals {

    // A `ready` right after a restart can come from a reconnection that never got the response, so it needs `streaming` first.
    private var awaitingStreaming = false

    fun onStatus(status: PendingResponseStatus): PageSignal = when (status) {
        is StartStream -> {
            awaitingStreaming = true
            PageSignal.CANCEL_ERROR_SETTLE
        }
        Streaming -> {
            awaitingStreaming = false
            PageSignal.NONE
        }
        Ready -> if (awaitingStreaming) PageSignal.NONE else PageSignal.READY
        Error, Blocked -> PageSignal.START_ERROR_SETTLE
        else -> PageSignal.NONE
    }
}
