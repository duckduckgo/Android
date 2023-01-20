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

package com.duckduckgo.autofill.impl

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.Callback
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.impl.domain.javascript.JavascriptCredentials
import com.duckduckgo.autofill.impl.jsbridge.AutofillMessagePoster
import com.duckduckgo.autofill.impl.jsbridge.request.AutofillDataRequest
import com.duckduckgo.autofill.impl.jsbridge.request.AutofillRequestParser
import com.duckduckgo.autofill.impl.jsbridge.request.AutofillStoreFormDataRequest
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillInputMainType.CREDENTIALS
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillInputSubType.PASSWORD
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillInputSubType.USERNAME
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillTriggerType
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillTriggerType.AUTOPROMPT
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillTriggerType.USER_INITIATED
import com.duckduckgo.autofill.impl.jsbridge.response.AutofillResponseWriter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

interface AutofillJavascriptInterface {

    @JavascriptInterface
    fun getAutofillData(requestString: String)

    fun injectCredentials(credentials: LoginCredentials)
    fun injectNoCredentials()

    fun cancelRetrievingStoredLogins()

    var callback: Callback?
    var webView: WebView?

    companion object {
        const val INTERFACE_NAME = "BrowserAutofill"
    }
}

@ContributesBinding(FragmentScope::class)
class AutofillStoredBackJavascriptInterface @Inject constructor(
    private val requestParser: AutofillRequestParser,
    private val autofillStore: AutofillStore,
    private val autofillMessagePoster: AutofillMessagePoster,
    private val autofillResponseWriter: AutofillResponseWriter,
    private val autofillDomainFormatter: AutofillDomainFormatter,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    private val currentUrlProvider: UrlProvider = WebViewUrlProvider(dispatcherProvider),
    private val autofillCapabilityChecker: AutofillCapabilityChecker,
) : AutofillJavascriptInterface {

    override var callback: Callback? = null
    override var webView: WebView? = null

    // coroutine jobs tracked for supporting cancellation
    private val getAutofillDataJob = ConflatedJob()
    private val storeFormDataJob = ConflatedJob()
    private val injectCredentialsJob = ConflatedJob()

    @JavascriptInterface
    override fun getAutofillData(requestString: String) {
        Timber.v("BrowserAutofill: getAutofillData called:\n%s", requestString)
        getAutofillDataJob += coroutineScope.launch(dispatcherProvider.default()) {
            if (!autofillCapabilityChecker.canInjectCredentialsToWebView()) {
                Timber.v("BrowserAutofill: getAutofillData called but feature is disabled")
                return@launch
            }

            val url = currentUrlProvider.currentUrl(webView)
            if (url == null) {
                Timber.w("Can't autofill as can't retrieve current URL")
                return@launch
            }

            val request = requestParser.parseAutofillDataRequest(requestString)
            val triggerType = convertTriggerType(request.trigger)

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
                    callback?.onCredentialsAvailableToInject(url, credentials, triggerType)
                }
            }
        }
    }

    private fun convertTriggerType(trigger: SupportedAutofillTriggerType): LoginTriggerType {
        return when (trigger) {
            USER_INITIATED -> LoginTriggerType.USER_INITIATED
            AUTOPROMPT -> LoginTriggerType.AUTOPROMPT
        }
    }

    private fun filterRequestedSubtypes(
        request: AutofillDataRequest,
        credentials: List<LoginCredentials>,
    ): List<LoginCredentials> {
        return when (request.subType) {
            USERNAME -> credentials.filterNot { it.username.isNullOrBlank() }
            PASSWORD -> credentials.filterNot { it.password.isNullOrBlank() }
        }
    }

    private fun handleUnknownRequestMainType(
        request: AutofillDataRequest,
        url: String,
    ) {
        Timber.w("Autofill type %s unsupported", request.mainType)
        callback?.noCredentialsAvailable(url)
    }

    @JavascriptInterface
    fun storeFormData(data: String) {
        Timber.i("storeFormData called, credentials provided to be persisted")

        storeFormDataJob += coroutineScope.launch(dispatcherProvider.default()) {
            if (!autofillCapabilityChecker.canSaveCredentialsFromWebView()) {
                Timber.v("BrowserAutofill: storeFormData called but feature is disabled")
                return@launch
            }

            val currentUrl = currentUrlProvider.currentUrl(webView) ?: return@launch
            val title = autofillDomainFormatter.extractDomain(currentUrl)

            val request = requestParser.parseStoreFormDataRequest(data)

            if (!request.isValid()) {
                Timber.w("Invalid data from storeFormData")
                return@launch
            }

            val jsCredentials = JavascriptCredentials(request.credentials!!.username, request.credentials.password)
            val credentials = jsCredentials.asLoginCredentials(currentUrl, title)

            withContext(dispatcherProvider.main()) {
                callback?.onCredentialsAvailableToSave(currentUrl, credentials)
            }
        }
    }

    private fun AutofillStoreFormDataRequest?.isValid(): Boolean {
        if (this == null || credentials == null) return false
        return !(credentials.username.isNullOrBlank() && credentials.password.isNullOrBlank())
    }

    override fun injectCredentials(credentials: LoginCredentials) {
        Timber.v("Informing JS layer with credentials selected")
        injectCredentialsJob += coroutineScope.launch(dispatcherProvider.default()) {
            val jsCredentials = credentials.asJsCredentials()
            autofillMessagePoster.postMessage(webView, autofillResponseWriter.generateResponseGetAutofillData(jsCredentials))
        }
    }

    override fun injectNoCredentials() {
        Timber.v("No credentials selected; informing JS layer")
        injectCredentialsJob += coroutineScope.launch(dispatcherProvider.default()) {
            autofillMessagePoster.postMessage(webView, autofillResponseWriter.generateEmptyResponseGetAutofillData())
        }
    }

    private fun LoginCredentials.asJsCredentials(): JavascriptCredentials {
        return JavascriptCredentials(
            username = username,
            password = password,
        )
    }

    override fun cancelRetrievingStoredLogins() {
        getAutofillDataJob.cancel()
    }

    private fun JavascriptCredentials.asLoginCredentials(
        url: String,
        title: String?,
    ): LoginCredentials {
        return LoginCredentials(
            id = null,
            domain = url,
            username = username,
            password = password,
            domainTitle = title,
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
