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

package com.duckduckgo.autofill.impl.importing.capability

import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.WebMessageListener
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface ImportGooglePasswordsCapabilityChecker {
    suspend fun webViewCapableOfImporting(): Boolean
}

@ContributesBinding(AppScope::class)
class DefaultImportGooglePasswordsCapabilityChecker @Inject constructor(
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
) : ImportGooglePasswordsCapabilityChecker {

    override suspend fun webViewCapableOfImporting(): Boolean {
        val webViewWebMessageSupport = webViewCapabilityChecker.isSupported(WebMessageListener)
        val webViewDocumentStartJavascript = webViewCapabilityChecker.isSupported(DocumentStartJavaScript)
        return webViewWebMessageSupport && webViewDocumentStartJavascript
    }
}
