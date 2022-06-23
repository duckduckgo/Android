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
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.domain.javascript.JavascriptCredentials
import com.duckduckgo.autofill.jsbridge.AutofillMessagePoster
import com.duckduckgo.autofill.jsbridge.request.AutofillRequestParser
import com.duckduckgo.autofill.jsbridge.request.SupportedAutofillInputMainType.CREDENTIALS
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
    private val emailManager: EmailManager,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    var callback: Callback? = null
) {

    var webView: WebView? = null
    private val getAutofillDataJob = ConflatedJob()

    @JavascriptInterface
    fun getAutofillData(requestString: String) {
        Timber.v("BrowserAutofill: getAutofillData called:\n%s", requestString)
        getAutofillDataJob += coroutineScope.launch {
            val request = requestParser.parseAutofillDataRequest(requestString)

            if (request.mainType != CREDENTIALS) {
                Timber.w("Autofill type %s unsupported", request.mainType)
                return@launch
            }

            val url = currentUrl()
            if (url == null) {
                Timber.w("Can't autofill as can't retrieve current URL")
                return@launch
            }

            val credentials = autofillStore.getCredentials(url)

            withContext(Dispatchers.Main) {
                if (credentials.isEmpty()) {
                    callback?.noCredentialsAvailable(url)
                } else {
                    callback?.onCredentialsAvailableToInject(credentials)
                }
            }
        }
    }

    suspend fun getRuntimeConfiguration(rawJs: String, url: String?): String {
        Timber.v("BrowserAutofill: getRuntimeConfiguration called")

        val contentScope = autofillResponseWriter.generateContentScope()
        val userUnprotectedDomains = autofillResponseWriter.generateUserUnprotectedDomains()
        val userPreferences = autofillResponseWriter.generateUserPreferences(autofillCredentials = determineIfAutofillEnabled())
        val availableInputTypes = generateAvailableInputTypes(url)

        return rawJs
            .replace("// INJECT contentScope HERE", contentScope)
            .replace("// INJECT userUnprotectedDomains HERE", userUnprotectedDomains)
            .replace("// INJECT userPreferences HERE", userPreferences)
            .replace("// INJECT availableInputTypes HERE", availableInputTypes)
    }

    private suspend fun generateAvailableInputTypes(url: String?): String {
        val credentialsAvailable = determineIfCredentialsAvailable(url)
        val emailAvailable = determineIfEmailAvailable()

        val json = autofillResponseWriter.generateResponseGetAvailableInputTypes(credentialsAvailable, emailAvailable).also {
            Timber.v("availableInputTypes for %s: \n%s", url, it)
        }
        return "availableInputTypes = $json"
    }

    private fun determineIfEmailAvailable(): Boolean = emailManager.isSignedIn()

    // in the future, we'll also tie this into feature toggles and remote config
    private fun determineIfAutofillEnabled(): Boolean = autofillStore.autofillEnabled

    private suspend fun determineIfCredentialsAvailable(url: String?): Boolean {
        val credentialsAvailable = if (url == null) {
            false
        } else {
            val savedCredentials = autofillStore.getCredentials(url)
            savedCredentials.isNotEmpty()
        }
        return credentialsAvailable
    }

    @JavascriptInterface
    fun storeFormData(data: String) {
        Timber.i("storeFormData called, credentials provided to be persisted")

        getAutofillDataJob += coroutineScope.launch {
            val currentUrl = currentUrl() ?: return@launch

            val request = requestParser.parseStoreFormDataRequest(data).credentials
            val jsCredentials = JavascriptCredentials(request.username, request.password)
            val credentials = jsCredentials.asLoginCredentials(currentUrl)

            withContext(Dispatchers.Main) {
                callback?.onCredentialsAvailableToSave(currentUrl, credentials)
            }
        }
    }

    fun injectCredentials(credentials: LoginCredentials) {
        getAutofillDataJob += coroutineScope.launch {
            val jsCredentials = credentials.asJsCredentials()
            autofillMessagePoster.postMessage(webView, autofillResponseWriter.generateResponseGetAutofillData(jsCredentials))
        }
    }

    fun injectNoCredentials() {
        getAutofillDataJob += coroutineScope.launch {
            autofillMessagePoster.postMessage(webView, autofillResponseWriter.generateEmptyResponseGetAutofillData())
        }
    }

    private fun LoginCredentials.asJsCredentials(): JavascriptCredentials {
        return JavascriptCredentials(
            username = username,
            password = password
        )
    }

    private fun JavascriptCredentials.asLoginCredentials(url: String): LoginCredentials {
        return LoginCredentials(
            id = null,
            domain = url,
            username = username,
            password = password
        )
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
