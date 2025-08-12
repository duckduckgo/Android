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

package com.duckduckgo.contentscopescripts.impl.messaging

import androidx.webkit.JavaScriptReplyProxy
import com.duckduckgo.contentscopescripts.api.GlobalContentScopeJsMessageHandlersPlugin
import com.duckduckgo.contentscopescripts.api.GlobalJsMessageHandler
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DebugFlagGlobalHandler @Inject constructor() : GlobalContentScopeJsMessageHandlersPlugin {

    override fun getGlobalJsMessageHandler(): GlobalJsMessageHandler = object : GlobalJsMessageHandler {

        override fun process(
            jsMessage: JsMessage,
            jsMessageCallback: JsMessageCallback,
            replyProxy: JavaScriptReplyProxy,
        ) {
            if (jsMessage.method == method) {
                jsMessageCallback.process(
                    featureName = jsMessage.featureName,
                    method = jsMessage.method,
                    id = jsMessage.id,
                    data = jsMessage.params,
                )
            }
        }

        override val method: String = "addDebugFlag"
    }
}
