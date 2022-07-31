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
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.domain.javascript.JavascriptCredentials
import com.duckduckgo.autofill.jsbridge.AutofillMessagePoster
import com.duckduckgo.autofill.jsbridge.request.AutofillDataRequest
import com.duckduckgo.autofill.jsbridge.request.AutofillRequestParser
import com.duckduckgo.autofill.jsbridge.request.SupportedAutofillInputMainType.CREDENTIALS
import com.duckduckgo.autofill.jsbridge.request.SupportedAutofillInputSubType.PASSWORD
import com.duckduckgo.autofill.jsbridge.request.SupportedAutofillInputSubType.USERNAME
import com.duckduckgo.autofill.jsbridge.response.AutofillResponseWriter
import com.duckduckgo.autofill.store.AutofillStore
import com.duckduckgo.deviceauth.api.DeviceAuthenticator
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

interface AutofillJavascriptInterface {

    @JavascriptInterface
    fun getAutofillData(requestString: String)

    suspend fun getRuntimeConfiguration(rawJs: String, url: String?): String
    fun injectCredentials(credentials: LoginCredentials)
    fun injectNoCredentials()

    var callback: Callback?
    var webView: WebView?

    companion object {
        const val INTERFACE_NAME = "BrowserAutofill"
    }

}

@ContributesBinding(AppScope::class)
class AutofillStoredBackJavascriptInterface @Inject constructor(
    private val requestParser: AutofillRequestParser,
    private val autofillStore: AutofillStore,
    private val autofillMessagePoster: AutofillMessagePoster,
    private val autofillResponseWriter: AutofillResponseWriter,
    private val emailManager: EmailManager,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val deviceAuthenticator: DeviceAuthenticator,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    private val currentUrlProvider: UrlProvider = WebViewUrlProvider(dispatcherProvider)
) : AutofillJavascriptInterface {

    override var callback: Callback? = null
    override var webView: WebView? = null
    private val getAutofillDataJob = ConflatedJob()

    @JavascriptInterface
    override fun getAutofillData(requestString: String) {
        Timber.v("BrowserAutofill: getAutofillData called:\n%s", requestString)
        getAutofillDataJob += coroutineScope.launch(dispatcherProvider.default()) {
            val url = currentUrlProvider.currentUrl(webView)
            if (url == null) {
                Timber.w("Can't autofill as can't retrieve current URL")
                return@launch
            }

            val request = requestParser.parseAutofillDataRequest(requestString)

            if (request.mainType != CREDENTIALS) {
                handleUnknownRequestMainType(request, url)
                return@launch
            }

            val allCredentials = autofillStore.getCredentials(url)
            val credentials = filterRequestedSubtypes(request, allCredentials)

            withContext(dispatcherProvider.main()) {
                if (credentials.isEmpty()) {
                    callback?.noCredentialsAvailable(url)
                } else {
                    callback?.onCredentialsAvailableToInject(credentials)
                }
            }
        }
    }

    private fun filterRequestedSubtypes(
        request: AutofillDataRequest,
        credentials: List<LoginCredentials>
    ): List<LoginCredentials> {
        return when (request.subType) {
            USERNAME -> credentials.filterNot { it.username.isNullOrBlank() }
            PASSWORD -> credentials.filterNot { it.password.isNullOrBlank() }
        }
    }

    private fun handleUnknownRequestMainType(
        request: AutofillDataRequest,
        url: String
    ) {
        Timber.w("Autofill type %s unsupported", request.mainType)
        callback?.noCredentialsAvailable(url)
    }

    override suspend fun getRuntimeConfiguration(
        rawJs: String,
        url: String?
    ): String {
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
    private fun determineIfAutofillEnabled(): Boolean = autofillStore.autofillEnabled && deviceAuthenticator.hasValidDeviceAuthentication()

    private suspend fun determineIfCredentialsAvailable(url: String?): Boolean {
        return if (url == null) {
            false
        } else {
            val savedCredentials = autofillStore.getCredentials(url)
            savedCredentials.isNotEmpty()
        }
    }

    @JavascriptInterface
    fun storeFormData(data: String) {
        Timber.i("storeFormData called, credentials provided to be persisted")

        getAutofillDataJob += coroutineScope.launch(dispatcherProvider.default()) {
            val currentUrl = currentUrlProvider.currentUrl(webView) ?: return@launch

            val request = requestParser.parseStoreFormDataRequest(data).credentials
            val jsCredentials = JavascriptCredentials(request.username, request.password)
            val credentials = jsCredentials.asLoginCredentials(currentUrl)

            withContext(dispatcherProvider.main()) {
                callback?.onCredentialsAvailableToSave(currentUrl, credentials)
            }
        }
    }

    override fun injectCredentials(credentials: LoginCredentials) {
        Timber.v("Informing JS layer with credentials selected")
        getAutofillDataJob += coroutineScope.launch(dispatcherProvider.default()) {
            val jsCredentials = credentials.asJsCredentials()
            autofillMessagePoster.postMessage(webView, autofillResponseWriter.generateResponseGetAutofillData(jsCredentials))
        }
    }

    override fun injectNoCredentials() {
        Timber.v("No credentials selected; informing JS layer")
        getAutofillDataJob += coroutineScope.launch(dispatcherProvider.default()) {
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

    interface UrlProvider {
        suspend fun currentUrl(webView: WebView?): String?
    }

    @ContributesBinding(AppScope::class)
    class WebViewUrlProvider @Inject constructor(val dispatcherProvider: DispatcherProvider) : UrlProvider {
        override suspend fun currentUrl(webView: WebView?): String? {
            return withContext(dispatcherProvider.main()) {
                webView?.url
            }
        }
    }
}
