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

import com.duckduckgo.contentscopescripts.api.contentscopeExperiments.ContentScopeExperiments
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScript
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScriptScriptStrategy
import com.duckduckgo.js.messaging.api.AddDocumentStartScriptDelegate
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(FragmentScope::class)
class ContentScopeScriptsAddDocumentStartJavaScript @Inject constructor(
    webViewCompatContentScopeScripts: WebViewCompatContentScopeScripts,
    contentScopeExperiments: ContentScopeExperiments,
    scriptInjectorDelegate: AddDocumentStartScriptDelegate,
) : AddDocumentStartJavaScript by scriptInjectorDelegate.createPlugin(
    object : AddDocumentStartJavaScriptScriptStrategy {
        override suspend fun canInject(): Boolean {
            return webViewCompatContentScopeScripts.isEnabled()
        }

        override suspend fun getScriptString(): String {
            val activeExperiments = contentScopeExperiments.getActiveExperiments()
            return webViewCompatContentScopeScripts.getScript(activeExperiments)
        }

        override val allowedOriginRules: Set<String> = setOf("*")

        override val context: String
            get() = "contentScopeScripts"
    },
)
