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

package com.duckduckgo.app.browser.privacypass

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacypass.api.PrivacyPassManager
import com.duckduckgo.privacypass.api.PrivacyPassResult
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

private const val PRIVACY_PASS_RETRY_HEADER = "X-DuckDuckGo-PrivacyPass-Retry"
private const val PRIVACY_PASS_RETRY_HEADER_VALUE = "1"

interface PrivacyPassHttpErrorHandler {
    fun handle(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
        requestUrl: String? = null,
    )
}

@ContributesBinding(AppScope::class)
class RealPrivacyPassHttpErrorHandler @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val privacyPassManager: PrivacyPassManager,
) : PrivacyPassHttpErrorHandler {

    override fun handle(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
        requestUrl: String?,
    ) {
        if (request?.isForMainFrame != true || errorResponse == null) return

        val challengeRequestUrl = requestUrl ?: request.url?.toString() ?: return
        val responseHeaders = errorResponse.responseHeaders.orEmpty()
        if (request.method != "GET" ||
            !privacyPassManager.isPrivateTokenChallenge(errorResponse.statusCode, responseHeaders)
        ) {
            return
        }

        val hasPrivacyPassRetryHeader = request.requestHeaders
            .orEmpty()
            .entries
            .firstOrNull { it.key.equals(PRIVACY_PASS_RETRY_HEADER, ignoreCase = true) }
            ?.value == PRIVACY_PASS_RETRY_HEADER_VALUE
        if (hasPrivacyPassRetryHeader) {
            logcat { "PrivacyPass: retry already attempted for ${request.url}, skipping to avoid loops" }
            return
        }

        val wwwAuth = responseHeaders.entries.firstOrNull {
            it.key.equals("WWW-Authenticate", ignoreCase = true)
        }?.value ?: return

        logcat { "PrivacyPass: 401 + PrivateToken challenge detected for $challengeRequestUrl" }
        appCoroutineScope.launch(dispatcherProvider.io()) {
            val result = privacyPassManager.handlePrivateTokenChallenge(
                originalUrl = challengeRequestUrl,
                wwwAuthenticateHeader = wwwAuth,
            )
            when (result) {
                is PrivacyPassResult.Success -> {
                    logcat { "PrivacyPass: retrying $challengeRequestUrl with authorization header" }
                    withContext(dispatcherProvider.main()) {
                        val headers = mutableMapOf(
                            "Authorization" to result.authorizationHeader,
                            PRIVACY_PASS_RETRY_HEADER to PRIVACY_PASS_RETRY_HEADER_VALUE,
                        )
                        view?.url?.let { currentUrl ->
                            headers["Referer"] = currentUrl
                        }
                        view?.loadUrl(challengeRequestUrl, headers)
                    }
                }

                is PrivacyPassResult.Failure -> {
                    logcat { "PrivacyPass: challenge handling failed — ${result.reason}" }
                }
            }
        }
    }
}
