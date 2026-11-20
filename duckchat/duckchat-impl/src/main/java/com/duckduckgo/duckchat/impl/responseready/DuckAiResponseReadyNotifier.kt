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

import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import logcat.logcat
import javax.inject.Inject

/** Tells the user that a Duck.ai response they left is ready, and withdraws that notice when it no longer applies. */
interface DuckAiResponseReadyNotifier {
    /**
     * [tabId] is the tab the response was left in. It may be closed already, or close before the user taps, so it is only a hint.
     * [chatId] identifies the response.
     */
    suspend fun notifyReady(tabId: String, chatId: String)
    suspend fun cancel(chatId: String)
}

/**
 * Only logs for now, concrete impl will follow in upcoming PRs.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DuckAiResponseReadyNotifier>())
class DuckAiResponseReadyNotifierImpl @Inject constructor() : DuckAiResponseReadyNotifier {
    override suspend fun notifyReady(tabId: String, chatId: String) {
        logcat { "Duck.ai response ready for tab=$tabId chat=$chatId" }
    }

    override suspend fun cancel(chatId: String) {
        logcat { "Duck.ai response ready cancelled for chat=$chatId" }
    }
}
