/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.js.messaging.impl

import android.webkit.WebView
import androidx.annotation.MainThread
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.JsMessageHelper
import com.duckduckgo.js.messaging.api.JsRequestResponse
import com.duckduckgo.js.messaging.api.JsRequestResponse.Error
import com.duckduckgo.js.messaging.api.JsRequestResponse.Success
import com.duckduckgo.js.messaging.api.SubscriptionEvent
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealJsMessageHelper @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : JsMessageHelper {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    override fun sendSubscriptionEvent(subscriptionEvent: SubscriptionEvent, callbackName: String, secret: String, webView: WebView) {
        val jsonAdapter: JsonAdapter<SubscriptionEvent> = moshi.adapter(SubscriptionEvent::class.java)
        val message = jsonAdapter.toJson(subscriptionEvent).toString()
        val response = buildJsResponse(message, callbackName, secret)
        sendResponse(response, webView)
    }

    private fun getMessage(response: JsRequestResponse): String {
        return when (response) {
            is Success -> {
                val jsonAdapter: JsonAdapter<Success> = moshi.adapter(Success::class.java)
                jsonAdapter.toJson(response).toString()
            }

            is Error -> {
                val jsonAdapterError: JsonAdapter<Error> = moshi.adapter(Error::class.java)
                jsonAdapterError.toJson(response).toString()
            }
        }
    }

    override fun sendJsResponse(jsRequestResponse: JsRequestResponse, callbackName: String, secret: String, webView: WebView) {
        val response = buildJsResponse(getMessage(jsRequestResponse), callbackName, secret)
        sendResponse(response, webView)
    }

    @MainThread
    private fun sendResponse(response: String, webView: WebView) {
        appCoroutineScope.launch(dispatcherProvider.main()) {
            webView.evaluateJavascript("javascript:$response", null)
        }
    }

    private fun buildJsResponse(message: String, callback: String, secret: String): String {
        return """
            (function() {
                window['$callback']('$secret', $message);
            })();
        """.trimIndent()
    }
}
