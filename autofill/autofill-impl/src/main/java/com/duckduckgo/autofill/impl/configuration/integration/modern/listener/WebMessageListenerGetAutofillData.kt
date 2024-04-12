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

package com.duckduckgo.autofill.impl.configuration.integration.modern.listener

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.AutofillWebMessageRequest
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType
import com.duckduckgo.autofill.impl.InternalAutofillCapabilityChecker
import com.duckduckgo.autofill.impl.deduper.AutofillLoginDeduplicator
import com.duckduckgo.autofill.impl.jsbridge.request.AutofillDataRequest
import com.duckduckgo.autofill.impl.jsbridge.request.AutofillRequestParser
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillInputMainType.CREDENTIALS
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillInputSubType.PASSWORD
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillInputSubType.USERNAME
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillTriggerType
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillTriggerType.AUTOPROMPT
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillTriggerType.USER_INITIATED
import com.duckduckgo.autofill.impl.jsbridge.response.AutofillResponseWriter
import com.duckduckgo.autofill.impl.sharedcreds.ShareableCredentials
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@SingleInstanceIn(FragmentScope::class)
@ContributesMultibinding(FragmentScope::class)
@SuppressLint("RequiresFeature")
class WebMessageListenerGetAutofillData @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val autofillCapabilityChecker: InternalAutofillCapabilityChecker,
    private val requestParser: AutofillRequestParser,
    private val autofillStore: InternalAutofillStore,
    private val shareableCredentials: ShareableCredentials,
    private val loginDeduplicator: AutofillLoginDeduplicator,
    private val responseWriter: AutofillResponseWriter,
) : AutofillWebMessageListener() {

    override val key: String
        get() = "ddgGetAutofillData"

    override fun onPostMessage(
        webView: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        reply: JavaScriptReplyProxy,
    ) {
        val originalUrl: String? = webView.url

        job += appCoroutineScope.launch(dispatchers.io()) {
            val requestId = storeReply(reply)

            getAutofillData(
                message.data.toString(),
                AutofillWebMessageRequest(
                    requestOrigin = sourceOrigin.toString(),
                    originalPageUrl = originalUrl,
                    requestId = requestId,
                ),
            )
        }
    }

    private suspend fun getAutofillData(requestString: String, autofillWebMessageRequest: AutofillWebMessageRequest) {
        Timber.v("BrowserAutofill: getAutofillData called:\n%s", requestString)
        if (autofillWebMessageRequest.originalPageUrl == null) {
            Timber.w("Can't autofill as can't retrieve current URL")
            return
        }

        if (!autofillCapabilityChecker.canInjectCredentialsToWebView(autofillWebMessageRequest.requestOrigin)) {
            Timber.v("BrowserAutofill: getAutofillData called but feature is disabled")
            return
        }

        val parseResult = requestParser.parseAutofillDataRequest(requestString)
        val request = parseResult.getOrElse {
            Timber.w(it, "Unable to parse getAutofillData request")
            return
        }

        val triggerType = convertTriggerType(request.trigger)

        if (request.mainType != CREDENTIALS) {
            handleUnknownRequestMainType(request, autofillWebMessageRequest)
            return
        }

        if (request.isGeneratedPasswordAvailable()) {
            handleRequestForPasswordGeneration(autofillWebMessageRequest, request)
        } else if (request.isAutofillCredentialsRequest()) {
            handleRequestForAutofillingCredentials(autofillWebMessageRequest, request, triggerType)
        } else {
            Timber.w("Unable to process request; don't know how to handle request %s", requestString)
        }
    }

    private suspend fun handleRequestForPasswordGeneration(
        autofillWebMessageRequest: AutofillWebMessageRequest,
        request: AutofillDataRequest,
    ) {
        callback.onGeneratedPasswordAvailableToUse(autofillWebMessageRequest, request.generatedPassword?.username, request.generatedPassword?.value!!)
    }

    private fun handleUnknownRequestMainType(
        request: AutofillDataRequest,
        autofillWebMessageRequest: AutofillWebMessageRequest,
    ) {
        Timber.w("Autofill type %s unsupported", request.mainType)
        onNoCredentialsAvailable(autofillWebMessageRequest)
    }

    private suspend fun handleRequestForAutofillingCredentials(
        urlRequest: AutofillWebMessageRequest,
        request: AutofillDataRequest,
        triggerType: LoginTriggerType,
    ) {
        val matches = mutableListOf<LoginCredentials>()
        val directMatches = autofillStore.getCredentials(urlRequest.requestOrigin)
        val shareableMatches = shareableCredentials.shareableCredentials(urlRequest.requestOrigin)
        Timber.v("Direct matches: %d, shareable matches: %d for %s", directMatches.size, shareableMatches.size, urlRequest.requestOrigin)
        matches.addAll(directMatches)
        matches.addAll(shareableMatches)

        val credentials = filterRequestedSubtypes(request, matches)

        val dedupedCredentials = loginDeduplicator.deduplicate(urlRequest.requestOrigin, credentials)
        Timber.v("Original autofill credentials list size: %d, after de-duping: %d", credentials.size, dedupedCredentials.size)

        val finalCredentialList = ensureUsernamesNotNull(dedupedCredentials)

        if (finalCredentialList.isEmpty()) {
            onNoCredentialsAvailable(urlRequest)
        } else {
            callback.onCredentialsAvailableToInject(urlRequest, finalCredentialList, triggerType)
        }
    }

    private fun onNoCredentialsAvailable(urlRequest: AutofillWebMessageRequest) {
        val message = responseWriter.generateEmptyResponseGetAutofillData()
        onResponse(message, urlRequest.requestId)
    }

    private fun convertTriggerType(trigger: SupportedAutofillTriggerType): LoginTriggerType {
        return when (trigger) {
            USER_INITIATED -> LoginTriggerType.USER_INITIATED
            AUTOPROMPT -> LoginTriggerType.AUTOPROMPT
        }
    }

    private fun ensureUsernamesNotNull(credentials: List<LoginCredentials>) =
        credentials.map {
            if (it.username == null) {
                it.copy(username = "")
            } else {
                it
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
}
