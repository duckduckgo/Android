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

package com.duckduckgo.duckchat.impl.clienttools

import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

interface ClientToolPermissionPrompt {
    /** Shows the prompt and returns the user's answer. Cancelling the caller withdraws the prompt. */
    suspend fun request(request: ClientToolPermissionRequest): ClientToolPermissionAnswer
}

data class ClientToolPermissionRequest(
    val origin: String,
    val toolName: String,
    val chatTabId: String,
    val copy: ClientToolPermissionMode.Ask,
)

enum class ClientToolPermissionAnswer {
    ALLOW_ONCE,
    ALWAYS,
    NEVER,
    DISMISSED,
}

/** Shows nothing, so an ASK tool without a remembered choice settles as cancelled. */
@ContributesBinding(AppScope::class)
class NoUiClientToolPermissionPrompt @Inject constructor() : ClientToolPermissionPrompt {
    override suspend fun request(request: ClientToolPermissionRequest): ClientToolPermissionAnswer = ClientToolPermissionAnswer.DISMISSED
}
