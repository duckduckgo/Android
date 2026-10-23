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

package com.duckduckgo.duckchat.impl.wideevents

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiSessionCallback
import com.duckduckgo.duckchat.api.DuckAiSessionExitTrigger
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import javax.inject.Inject

/**
 * The single [DuckAiSessionCallback] binding, so each wide event stays unaware of the others.
 * The session event is called first so its behaviour never depends on the response-wait event.
 */
@ContributesBinding(AppScope::class, binding = binding<DuckAiSessionCallback>())
class DuckAiSessionCallbackFanOut @Inject constructor(
    private val session: RealDuckAiSessionWideEvent,
    private val responseWait: DuckAiResponseWaitWideEventImpl,
) : DuckAiSessionCallback {

    override fun onLaunchLandingResolved(tabId: String?, url: String?) {
        session.onLaunchLandingResolved(tabId, url)
        responseWait.onLaunchLandingResolved(tabId, url)
    }

    override fun onDuckAiPageVisible(tabId: String, url: String) {
        session.onDuckAiPageVisible(tabId, url)
        responseWait.onDuckAiPageVisible(tabId, url)
    }

    override fun onSelectedTabChanged(tabId: String?, url: String?) {
        session.onSelectedTabChanged(tabId, url)
        responseWait.onSelectedTabChanged(tabId, url)
    }

    override fun onExitIntent(tabId: String, trigger: DuckAiSessionExitTrigger) {
        session.onExitIntent(tabId, trigger)
        responseWait.onExitIntent(tabId, trigger)
    }

    override fun onPromptSubmitted(tabId: String) {
        session.onPromptSubmitted(tabId)
        responseWait.onPromptSubmitted(tabId)
    }

    override fun onNewChatCreated(tabId: String) {
        session.onNewChatCreated(tabId)
        responseWait.onNewChatCreated(tabId)
    }
}
