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

package com.duckduckgo.app.browser

import androidx.lifecycle.LifecycleOwner
import androidx.webkit.WebViewFeature
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.WebMessageListener
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.compareSemanticVersion
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesMultibinding(AppScope::class, MainProcessLifecycleObserver::class)
@ContributesBinding(AppScope::class, WebViewCapabilityChecker::class)
@SingleInstanceIn(AppScope::class)
class RealWebViewCapabilityChecker @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val webViewVersionProvider: WebViewVersionProvider,
    private val pixel: Pixel,
) : WebViewCapabilityChecker, MainProcessLifecycleObserver {
    override suspend fun isSupported(capability: WebViewCapability): Boolean =
        when (capability) {
            DocumentStartJavaScript -> isDocumentStartJavaScriptSupported()
            WebMessageListener -> isWebMessageListenerSupported()
        }

    override fun onStart(owner: LifecycleOwner) {
        reportWebViewCapabilities()
    }

    private fun reportWebViewCapabilities() {
        coroutineScope.launch(dispatchers.io()) {
            val params = mapOf(
                "version" to webViewVersionProvider.getFullVersion(),
                "multi_profile" to isMultiProfileSupported().toString(),
                "delete_browsing_data" to isDeleteBrowsingDataSupported().toString(),
            )
            pixel.fire(pixel = WebViewCapabilityPixelName.WEBVIEW_CAPABILITIES, parameters = params, type = Pixel.PixelType.Daily())
        }
    }

    private suspend fun isWebMessageListenerSupported(): Boolean =
        withContext(dispatchers.io()) {
            webViewVersionProvider
                .getFullVersion()
                .compareSemanticVersion(WEB_MESSAGE_LISTENER_WEBVIEW_VERSION)
                ?.let { it >= 0 } ?: false
        } && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    private fun isDocumentStartJavaScriptSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    private fun isMultiProfileSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    private fun isDeleteBrowsingDataSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)

    companion object {
        // critical fixes didn't exist until this WebView version. See https://issues.chromium.org/issues/338340758#comment42
        private const val WEB_MESSAGE_LISTENER_WEBVIEW_VERSION = "126.0.6478.40"
    }
}

enum class WebViewCapabilityPixelName(override val pixelName: String) : Pixel.PixelName {
    WEBVIEW_CAPABILITIES("webview_capabilities"),
}

@ContributesMultibinding(AppScope::class)
class WebViewCapabilityPixelParamRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            WebViewCapabilityPixelName.WEBVIEW_CAPABILITIES.pixelName to PixelParameter.removeAll(),
        )
    }
}
