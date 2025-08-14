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

package com.duckduckgo.webcompat.impl.messaging.webviewcompat

import com.duckduckgo.contentscopescripts.api.WebCompatContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.WebCompatMessageHandler
import com.duckduckgo.js.messaging.api.WebViewCompatMessageCallback
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import org.json.JSONObject

@ContributesMultibinding(AppScope::class)
class WebViewCompatWebCompatContentScopeJsMessageHandler @Inject constructor() : WebCompatContentScopeJsMessageHandlersPlugin {

    override fun getJsMessageHandler(): WebCompatMessageHandler = object : WebCompatMessageHandler {

        override fun process(
            jsMessage: JsMessage,
            jsMessageCallback: WebViewCompatMessageCallback?,
            onResponse: (JSONObject) -> Unit,
        ) {
            if (jsMessage.id == null) return
            jsMessageCallback?.process(featureName, jsMessage.method, jsMessage.id, jsMessage.params, onResponse)
        }

        override val featureName: String = "webCompat"
        override val methods: List<String> = listOf("webShare", "permissionsQuery", "screenLock", "screenUnlock")
    }
}
