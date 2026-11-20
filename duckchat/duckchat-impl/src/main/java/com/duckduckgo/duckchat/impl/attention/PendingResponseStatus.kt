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

package com.duckduckgo.duckchat.impl.attention

internal sealed interface PendingResponseStatus {

    /** Any `start_stream:` status. A restart is an edit, a regenerate, a model switch or a second opinion. */
    data class StartStream(val isRestart: Boolean) : PendingResponseStatus

    data object Loading : PendingResponseStatus
    data object Streaming : PendingResponseStatus

    data object Ready : PendingResponseStatus
    data object Error : PendingResponseStatus
    data object Blocked : PendingResponseStatus
    data object Other : PendingResponseStatus

    val isProgress: Boolean get() = this is Loading || this is Streaming || this is StartStream
    val isTerminal: Boolean get() = this is Ready || this is Error || this is Blocked

    companion object {
        private const val START_STREAM_PREFIX = "start_stream:"
        private const val RESTART_STREAM = "start_stream:restart_stream"

        fun from(raw: String): PendingResponseStatus = when {
            raw == "loading" -> Loading
            raw == "streaming" -> Streaming
            raw.startsWith(START_STREAM_PREFIX) -> StartStream(isRestart = raw == RESTART_STREAM)
            raw == "ready" -> Ready
            raw == "error" -> Error
            raw == "blocked" -> Blocked
            else -> Other
        }
    }
}
