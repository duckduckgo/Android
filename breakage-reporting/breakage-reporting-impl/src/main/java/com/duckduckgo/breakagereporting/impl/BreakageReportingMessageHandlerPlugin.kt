/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.breakagereporting.impl

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHandler
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope

@ContributesBinding(ActivityScope::class)
@Named("breakageMessageHandler")
class BreakageReportingMessageHandlerPlugin @Inject constructor(
    @AppCoroutineScope val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : JsMessageHandler {

    override fun process(
        jsMessage: JsMessage,
        secret: String,
        jsMessageCallback: JsMessageCallback?,
    ) {
        jsMessageCallback?.process(featureName, jsMessage.method, jsMessage.id, jsMessage.params)
    }

    override val allowedDomains: List<String> = emptyList()
    override val featureName: String = "breakageReporting"
    override val methods: List<String> = listOf("breakageReportResult")
}
