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

import android.view.View
import androidx.fragment.app.Fragment

/**
 * The browser's contextual Duck.ai surface. The host reports the entry-point tap and embeds the
 * sheet fragment it is handed; everything else (the entry menu, the redesign decision, opening a new
 * chat tab, and the sheet implementation) lives in the Duck.ai feature module.
 */
interface DuckChatContextual {

    /**
     * Launches the contextual Duck.ai flow. Depending on config this either shows the entry menu
     * (anchored to [anchor]) or opens the contextual sheet directly.
     * [sourceTabId] is the host tab the flow was launched from; a new chat tab is anchored to it so
     * closing that tab returns here rather than leaving an orphan tab.
     * [showChatSurface] is invoked (on the launching host) when the embedded contextual
     * sheet should be shown. A null [anchor] (no entry-point view, e.g. from native input) always
     * opens the sheet directly.
     */
    suspend fun launch(
        sourceTabId: String,
        anchor: View?,
        showChatSurface: () -> Unit,
    )

    /**
     * Creates the chat-in-progress surface of the contextual sheet for [tabId] — the fragment hosting
     * the Duck.ai conversation. The host embeds it in its own bottom-sheet container and drives its
     * visibility.
     *
     * The fragment reports the "open in full-screen Duck.ai" outcome back to the host via the Fragment
     * Result API, keyed by [RESULT_KEY] with the URL under [RESULT_URL].
     */
    fun createChatSurface(tabId: String): Fragment

    companion object {
        const val RESULT_KEY: String = "KEY_DUCK_AI_CONTEXTUAL_RESULT"
        const val RESULT_URL: String = "KEY_DUCK_AI_URL"
    }
}
