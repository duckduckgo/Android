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

package com.duckduckgo.pir.impl.dashboard.messaging.handlers

import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebConstants

/**
 * Implement this class and contribute it as a multibinding to handle specific messages that are received from the PIR webview.
 *
 * Apply this annotation to your actual handler class:
 *
 * ```
 * @ContributesMultibinding(
 *     scope = ActivityScope::class,
 *     boundType = PirWebJsMessageHandler::class,
 * )
 * ```
 */
abstract class PirWebJsMessageHandler : JsMessageHandler {

    override fun process(
        jsMessage: JsMessage,
        secret: String,
        jsMessageCallback: JsMessageCallback?,
    ) {
        // Use the new process method instead
    }

    abstract fun process(
        jsMessage: JsMessage,
        jsMessaging: JsMessaging,
        jsMessageCallback: JsMessageCallback?,
    )

    override val allowedDomains: List<String> = emptyList()
    override val featureName: String = PirDashboardWebConstants.SCRIPT_FEATURE_NAME
}
