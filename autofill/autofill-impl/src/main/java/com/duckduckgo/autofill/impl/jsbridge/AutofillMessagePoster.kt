/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.impl.jsbridge

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat

interface AutofillMessagePoster {
    suspend fun postMessage(
        webView: WebView?,
        message: String,
    )
}

@ContributesBinding(AppScope::class)
class AutofillWebViewMessagePoster @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : AutofillMessagePoster {

    @SuppressLint("RequiresFeature")
    override suspend fun postMessage(
        webView: WebView?,
        message: String,
    ) {
        webView?.let { wv ->
            withContext(dispatchers.main()) {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.POST_WEB_MESSAGE)) {
                    logcat(ERROR) { "Unable to post web message" }
                    return@withContext
                }

                WebViewCompat.postWebMessage(wv, WebMessageCompat(message), WILDCARD_ORIGIN_URL)
            }
        }
    }

    companion object {
        private val WILDCARD_ORIGIN_URL = "*".toUri()
    }
}
