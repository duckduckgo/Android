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

package com.duckduckgo.autofill.impl.passkey

import com.duckduckgo.contentscopescripts.api.ContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

/**
 * Handles the `webCompat` / `passkeyUsed` notification from content-scope-scripts.
 *
 * This is a notification rather than a request, so it carries no `id` and needs no
 * response - unlike the request/response methods registered by web-compat's own handler,
 * which is why this is a separate plugin rather than an addition to that method list.
 */
@ContributesMultibinding(AppScope::class)
class PasskeyUsedContentScopeJsMessageHandler @Inject constructor(
    private val logger: PasskeyUsedMessageLogger,
) : ContentScopeJsMessageHandlersPlugin {

    override fun getJsMessageHandler(): JsMessageHandler = object : JsMessageHandler {
        override fun process(
            jsMessage: JsMessage,
            jsMessaging: JsMessaging,
            jsMessageCallback: JsMessageCallback?,
        ) {
            logger.log(jsMessage.params)
        }

        override val allowedDomains: List<String> = emptyList()
        override val featureName: String = FEATURE_NAME
        override val methods: List<String> = listOf(METHOD_PASSKEY_USED)
    }

    private companion object {
        const val FEATURE_NAME = "webCompat"
        const val METHOD_PASSKEY_USED = "passkeyUsed"
    }
}
