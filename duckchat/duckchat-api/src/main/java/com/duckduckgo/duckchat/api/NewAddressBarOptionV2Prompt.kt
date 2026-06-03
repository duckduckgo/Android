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

package com.duckduckgo.duckchat.api

import kotlinx.coroutines.flow.Flow

/**
 * Lets the V2 new-address-bar picker be coordinated by the modal system in `:duckchat-impl` while its
 * UI lives in `:app`. The impl decides when to show (via the modal coordinator) and emits [commands];
 * the app observes them, renders the bottom sheet, and reports the user's choice back via [onConfirmed].
 */
interface NewAddressBarOptionV2Prompt {
    val commands: Flow<Command>

    /**
     * @param searchAndAiSelected `true` when the user confirmed Search + Duck.ai (which enables the input
     * screen toggle), `false` for Search only.
     */
    suspend fun onConfirmed(searchAndAiSelected: Boolean)

    sealed class Command {
        data object ShowPicker : Command()
    }
}
