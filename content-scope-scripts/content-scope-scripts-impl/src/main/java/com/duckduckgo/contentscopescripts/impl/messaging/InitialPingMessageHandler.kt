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

import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(ActivityScope::class)
class InitialPingMessageHandler @Inject constructor() : JsMessageHandler {
    override fun process(
        jsMessage: com.duckduckgo.js.messaging.api.JsMessage,
        jsMessaging: com.duckduckgo.js.messaging.api.JsMessaging,
        jsMessageCallback: com.duckduckgo.js.messaging.api.JsMessageCallback?,
    ) {
        if (jsMessage.id == null) return
        logcat("Cris") { "initial ping received" }
        jsMessageCallback?.process(featureName, jsMessage.method, jsMessage.id, jsMessage.params)
    }

    override val allowedDomains: List<String> = emptyList()
    override val featureName: String = "messaging"
    override val methods: List<String> = listOf("initialPing")
}
