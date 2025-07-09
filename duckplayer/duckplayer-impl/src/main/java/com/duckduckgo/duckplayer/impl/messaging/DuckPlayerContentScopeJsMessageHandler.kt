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

package com.duckduckgo.duckplayer.impl.messaging

import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckplayer.api.YOUTUBE_HOST
import com.duckduckgo.duckplayer.api.YOUTUBE_MOBILE_HOST
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DuckPlayerContentScopeJsMessageHandler @Inject constructor() : ContentScopeJsMessageHandlersPlugin {
    override fun getJsMessageHandler(): JsMessageHandler = object : JsMessageHandler {
        override fun process(jsMessage: JsMessage, secret: String, jsMessageCallback: JsMessageCallback?) {
            jsMessageCallback?.process(featureName, jsMessage.method, jsMessage.id ?: "", jsMessage.params)
        }

        override val allowedDomains: List<String> = listOf(
            AppUrl.Url.HOST,
            YOUTUBE_HOST,
            YOUTUBE_MOBILE_HOST,
        )

        override val featureName: String = "duckPlayer"
        override val methods: List<String> = listOf(
            "getUserValues",
            "sendDuckPlayerPixel",
            "setUserValues",
            "openDuckPlayer",
            "openInfo",
            "initialSetup",
            "reportPageException",
            "reportInitException",
        )
    }
}
