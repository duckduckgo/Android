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

package com.duckduckgo.app.browser.webview

import androidx.annotation.UiThread
import androidx.lifecycle.LifecycleOwner
import androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
import androidx.webkit.WebViewFeature.POST_WEB_MESSAGE
import androidx.webkit.WebViewFeature.USER_AGENT_METADATA
import androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER
import androidx.webkit.WebViewFeature.isFeatureSupported
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class WebViewFeatureFlagReporter @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver {

    @UiThread
    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) {
            pixel.fire(
                pixelName = AppPixelName.WEBVIEW_FEATURE_FLAGS_INJECTION_CAPABILITY.pixelName,
                parameters = buildParams(),
                encodedParameters = emptyMap(),
                type = PixelType.UNIQUE,
            )
        }
    }

    private fun buildParams(): Map<String, String> {
        return mapOf(
            "supportsWebMessageListener" to isFeatureSupported(WEB_MESSAGE_LISTENER).toString(),
            "supportsPostWebMessage" to isFeatureSupported(POST_WEB_MESSAGE).toString(),
            "supportsDocumentStartJavascript" to isFeatureSupported(DOCUMENT_START_SCRIPT).toString(),
            "supportsUserAgentMetadata" to isFeatureSupported(USER_AGENT_METADATA).toString(),
        )
    }
}
