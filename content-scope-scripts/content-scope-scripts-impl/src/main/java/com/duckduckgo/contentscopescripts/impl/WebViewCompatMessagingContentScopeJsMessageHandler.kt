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

package com.duckduckgo.contentscopescripts.impl

import com.duckduckgo.contentscopescripts.api.WebViewCompatContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.ProcessResult
import com.duckduckgo.js.messaging.api.ProcessResult.SendToConsumer
import com.duckduckgo.js.messaging.api.WebViewCompatMessageHandler
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(ActivityScope::class)
class WebViewCompatMessagingContentScopeJsMessageHandler @Inject constructor() : WebViewCompatContentScopeJsMessageHandlersPlugin {

    override fun getJsMessageHandler(): WebViewCompatMessageHandler = object : WebViewCompatMessageHandler {

        override fun process(
            jsMessage: JsMessage,
        ): ProcessResult {
            return SendToConsumer
        }

        override val featureName: String = "messaging"
        override val methods: List<String> = listOf("initialPing")
    }
}
