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

package com.duckduckgo.request.interception.api

import android.net.Uri

interface RequestBlockerPlugin {

    suspend fun evaluate(request: RequestBlockerRequest): Decision

    sealed class Decision {
        data object Ignore : Decision()
        data class Block(val reason: BlockReason) : Decision()
    }

    sealed class BlockReason {
        data class UserBlocked(val blockedDomain: String) : BlockReason()
    }
}

data class RequestBlockerRequest(
    val url: Uri,
    val documentUrl: Uri?,
    val isForMainFrame: Boolean,
    val requestHeaders: Map<String, String>,
)
