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

package com.duckduckgo.app.browser.webview

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.testseeder.api.TestSeederKey
import com.duckduckgo.testseeder.api.TestSeederPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

// Enables WebView remote debugging at launch (before any WebView is created) when a Maestro flow
// passes `webContentDebugging: "true"`. This is what makes `androidWebViewHierarchy: devtools` able
// to read WebView DOM content on pages whose accessibility tree isn't exposed on the CI WebView image.
// Writing the stored state directly (like Dev Settings does) is deterministic — no remote-config
// fetch/timing dependency.
@ContributesMultibinding(AppScope::class)
class WebContentDebuggingSeederPlugin @Inject constructor(
    private val webContentDebuggingFeature: WebContentDebuggingFeature,
) : TestSeederPlugin {

    override val handledKeys = setOf(TestSeederKey.WEB_CONTENT_DEBUGGING.key)

    override suspend fun apply(key: String, value: String) {
        webContentDebuggingFeature.webContentDebugging().setRawStoredState(Toggle.State(enable = value == "true"))
    }
}
