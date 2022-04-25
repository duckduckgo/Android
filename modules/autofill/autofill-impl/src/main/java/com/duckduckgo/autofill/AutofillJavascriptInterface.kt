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

package com.duckduckgo.autofill

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.autofill.jsbridge.AutofillMessagePoster
import com.duckduckgo.autofill.jsbridge.request.AutofillRequestParser
import com.duckduckgo.autofill.jsbridge.response.AutofillResponseWriter
import com.duckduckgo.autofill.store.AutofillStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class AutofillJavascriptInterface(
    private val requestParser: AutofillRequestParser,
    private val autofillStore: AutofillStore,
    private val autofillMessagePoster: AutofillMessagePoster,
    private val autofillResponseWriter: AutofillResponseWriter,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    var callback: Callback? = null
) {

    var webView: WebView? = null
    private val getAutofillDataJob = ConflatedJob()

    @JavascriptInterface
    fun getAutofillData(requestString: String) {
        Timber.i("BrowserAutofill: getAutofillData called:\n%s", requestString)
        getAutofillDataJob += coroutineScope.launch {
            val request = requestParser.parseRequest(requestString)
            Timber.i("Parsed request\ninputType: %s\nsubType: %s", request.mainType, request.subType)

            val url = currentUrl()
            if (url == null) {
                Timber.w("Can't autofill as can't retrieve current URL")
                return@launch
            }

            val credentials = autofillStore.getCredentials(url)

            withContext(Dispatchers.Main) {
                callback?.onCredentialsAvailable(credentials)
            }
        }
    }

    /**
     * Requested from JS; requesting to know if we have autofill data saved.
     *
     * When called, we need to determine if we have any saved data to autofill.
     * The response is NOT specifying whether we support something generally,
     * but specifically if we have autofill data for the current page.
     *
     * Currently, the response is returned synchronously;
     * in future this will be an async response using WebMessages.
     *
     * Currently, we only care about logins but in future there might be other autofill types to return.
     */
    @JavascriptInterface
    fun getAvailableInputTypes() {
        Timber.i("BrowserAutofill: getAvailableInputTypes called")
        getAutofillDataJob += coroutineScope.launch {
            val url = currentUrl()
            val credentialsAvailable = if (url == null) {
                false
            } else {
                val savedCredentials = autofillStore.getCredentials(url)
                savedCredentials.isNotEmpty()
            }

            Timber.v("Credentials available for %s: %s", url, credentialsAvailable)

            val message = autofillResponseWriter.generateResponseGetAvailableInputTypes(credentialsAvailable)
            autofillMessagePoster.postMessage(webView, message)
        }
    }

    @JavascriptInterface
    fun getRuntimeConfiguration() {
        Timber.i("BrowserAutofill: getRuntimeConfiguration called")
        getAutofillDataJob += coroutineScope.launch {
            val config = autofillResponseWriter.generateResponseGetRuntimeConfiguration()
            autofillMessagePoster.postMessage(webView, config)
        }
    }

    fun injectCredentials(credentials: Credentials) {
        getAutofillDataJob += coroutineScope.launch {
            autofillMessagePoster.postMessage(webView, autofillResponseWriter.generateResponseGetAutofillData(credentials))
        }
    }

    private suspend fun currentUrl(): String? {
        return withContext(Dispatchers.Main) {
            webView?.url
        }
    }

    companion object {
        const val INTERFACE_NAME = "BrowserAutofill"
    }

}
