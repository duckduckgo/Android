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

import android.annotation.SuppressLint
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.contentscopescripts.impl.WebViewCompatContentScopeScripts
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.PostMessageWrapperPlugin
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.js.messaging.api.WebMessagingPlugin
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesBinding(AppScope::class)
class ContentScopeScriptsPostMessageWrapperPlugin(
    @Named("contentScopeScrips") private val webMessagingPlugin: WebMessagingPlugin,
    private val contentScopeScriptsJsMessaging: ContentScopeScriptsJsMessaging,
    private val webViewCompatContentScopeScripts: WebViewCompatContentScopeScripts,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : PostMessageWrapperPlugin {
    @SuppressLint("PostMessageUsage")
    override fun postMessage(message: SubscriptionEventData) {
        coroutineScope.launch {
            if (webViewCompatContentScopeScripts.isEnabled()) {
                webMessagingPlugin.postMessage(message)
            } else {
                contentScopeScriptsJsMessaging.sendSubscriptionEvent(message)
            }
        }
    }

    override val context: String
        get() = if (webMessagingPlugin.context == contentScopeScriptsJsMessaging.context) {
            webMessagingPlugin.context
        } else {
            throw Exception("Mismatched contexts between WebMessagingPlugin and ContentScopeScriptsJsMessaging")
        }
}
