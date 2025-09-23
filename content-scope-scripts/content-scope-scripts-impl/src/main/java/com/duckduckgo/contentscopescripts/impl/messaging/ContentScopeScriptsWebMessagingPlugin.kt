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

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.WebViewCompatContentScopeJsMessageHandlersPlugin
import com.duckduckgo.contentscopescripts.impl.WebViewCompatContentScopeScripts
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.js.messaging.api.GlobalJsMessageHandler
import com.duckduckgo.js.messaging.api.WebMessagingPlugin
import com.duckduckgo.js.messaging.api.WebMessagingPluginDelegate
import com.duckduckgo.js.messaging.api.WebMessagingPluginStrategy
import com.duckduckgo.js.messaging.api.WebViewCompatMessageHandler
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import javax.inject.Named

@Named("contentScopeScripts")
@SingleInstanceIn(FragmentScope::class)
@ContributesBinding(FragmentScope::class)
@ContributesMultibinding(scope = FragmentScope::class, ignoreQualifier = true)
class ContentScopeScriptsWebMessagingPlugin @Inject constructor(
    handlers: PluginPoint<WebViewCompatContentScopeJsMessageHandlersPlugin>,
    globalHandlers: PluginPoint<GlobalContentScopeJsMessageHandlersPlugin>,
    webViewCompatContentScopeScripts: WebViewCompatContentScopeScripts,
    webMessagingPluginDelegate: WebMessagingPluginDelegate,
) : WebMessagingPlugin by webMessagingPluginDelegate.createPlugin(
    object : WebMessagingPluginStrategy {
        override val context: String = "contentScopeScripts"
        override val allowedDomains: Set<String> = setOf("*")
        override val objectName: String
            get() = "contentScopeAdsjs"

        override suspend fun canHandleMessaging(): Boolean {
            return webViewCompatContentScopeScripts.isEnabled()
        }

        override fun getMessageHandlers(): List<WebViewCompatMessageHandler> {
            return handlers.getPlugins().map { it.getJsMessageHandler() }
        }

        override fun getGlobalMessageHandler(): List<GlobalJsMessageHandler> {
            return globalHandlers.getPlugins().map { it.getGlobalJsMessageHandler() }
        }
    },
)
