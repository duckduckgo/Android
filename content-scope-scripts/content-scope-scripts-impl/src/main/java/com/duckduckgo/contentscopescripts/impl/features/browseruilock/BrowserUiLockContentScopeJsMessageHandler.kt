/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.impl.features.browseruilock

import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

/**
 * Message handler for the browserUiLock C-S-S feature.
 *
 * Handles 'uiLockChanged' notifications from the page when CSS signals
 * indicate the page wants to manage its own touch interactions.
 *
 * The handler forwards the message to the [JsMessageCallback] which is
 * implemented by BrowserTabFragment to update the UI lock state.
 */
@ContributesMultibinding(ActivityScope::class)
class BrowserUiLockContentScopeJsMessageHandler @Inject constructor() : ContentScopeJsMessageHandlersPlugin {

    override fun getJsMessageHandler(): JsMessageHandler = object : JsMessageHandler {
        override fun process(
            jsMessage: JsMessage,
            jsMessaging: JsMessaging,
            jsMessageCallback: JsMessageCallback?,
        ) {
            jsMessageCallback?.process(featureName, jsMessage.method, jsMessage.id, jsMessage.params)
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = FEATURE_NAME
        override val methods: List<String> = listOf(METHOD_UI_LOCK_CHANGED)
    }

    companion object {
        const val FEATURE_NAME = "browserUiLock"
        const val METHOD_UI_LOCK_CHANGED = "uiLockChanged"
    }
}
