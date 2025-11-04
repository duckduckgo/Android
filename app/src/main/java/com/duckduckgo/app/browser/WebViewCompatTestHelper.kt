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

package com.duckduckgo.app.browser

import com.duckduckgo.app.browser.webview.WebViewCompatFeature
import com.duckduckgo.app.browser.webview.WebViewCompatFeatureSettings
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val delay = "\$DELAY$"
private const val postInitialPing = "\$POST_INITIAL_PING$"
private const val replyToNativeMessages = "\$REPLY_TO_NATIVE_MESSAGES$"

interface WebViewCompatTestHelper {
    suspend fun configureWebViewForWebViewCompatTest(webView: DuckDuckGoWebView)
}

@ContributesBinding(FragmentScope::class)
@SingleInstanceIn(FragmentScope::class)
class RealWebViewCompatTestHelper @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val webViewCompatFeature: WebViewCompatFeature,
    private val webViewCompatWrapper: WebViewCompatWrapper,
    moshi: Moshi,
) : WebViewCompatTestHelper {

    private val adapter = moshi.adapter(WebViewCompatFeatureSettings::class.java)

    override suspend fun configureWebViewForWebViewCompatTest(webView: DuckDuckGoWebView) {
        val script = withContext(dispatchers.io()) {
            if (!webViewCompatFeature.self().isEnabled()) return@withContext null

            val webViewCompatSettings = webViewCompatFeature.self().getSettings()?.let {
                adapter.fromJson(it)
            }
            webView.resources?.openRawResource(R.raw.webviewcompat_test_script)?.bufferedReader().use { it?.readText() }.orEmpty()
                .replace(delay, webViewCompatSettings?.jsInitialPingDelay?.toString() ?: "0")
                .replace(postInitialPing, webViewCompatFeature.jsSendsInitialPing().isEnabled().toString())
                .replace(replyToNativeMessages, webViewCompatFeature.jsRepliesToNativeMessages().isEnabled().toString())
        } ?: return

        withContext(dispatchers.main()) {
            webViewCompatWrapper.addDocumentStartJavaScript(webView, script, setOf("*"))
        }
    }
}
