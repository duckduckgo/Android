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

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewCompat.WebMessageListener
import androidx.webkit.WebViewFeature
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.BrowserAutofill
import com.duckduckgo.autofill.api.Callback
import com.duckduckgo.autofill.api.EmailProtectionInContextSignupFlowListener
import com.duckduckgo.autofill.api.EmailProtectionUserPromptListener
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.passwordgeneration.AutomaticSavedLoginsMonitor
import com.duckduckgo.autofill.impl.configuration.AutofillRuntimeConfigProvider
import com.duckduckgo.autofill.impl.jsbridge.AutofillMessagePoster
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import timber.log.Timber

@ContributesBinding(FragmentScope::class)
class InlineBrowserAutofill @Inject constructor(
    private val autofillInterface: AutofillJavascriptInterface,
    private val autoSavedLoginsMonitor: AutomaticSavedLoginsMonitor,
    private val autofillRuntimeConfigProvider: AutofillRuntimeConfigProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val messagePoster: AutofillMessagePoster
) : BrowserAutofill {

    override fun addJsInterface(
        webView: WebView,
        autofillCallback: Callback,
        emailProtectionInContextCallback: EmailProtectionUserPromptListener?,
        emailProtectionInContextSignupFlowCallback: EmailProtectionInContextSignupFlowListener?,
        tabId: String,
    ) {
        Timber.v("Injecting BrowserAutofill interface")
        // Adding the interface regardless if the feature is available or not
        webView.addJavascriptInterface(autofillInterface, AutofillJavascriptInterface.INTERFACE_NAME)
        autofillInterface.webView = webView
        autofillInterface.callback = autofillCallback
        autofillInterface.emailProtectionInContextCallback = emailProtectionInContextCallback
        autofillInterface.autoSavedLoginsMonitor = autoSavedLoginsMonitor
        autofillInterface.tabId = tabId

        if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))  {
            addWebMessageListenerGetAutofillConfig(webView)
            addWebMessageListenerGetAutofillData(webView)
            addWebMessageListenerGetInContextDismissedAt(webView)
        } else {
            Timber.w("cdr cannot add web message listeners, feature not supported")
        }

    }

    @SuppressLint("RequiresFeature")
    private fun addWebMessageListenerGetAutofillConfig(webView: WebView) {
        WebViewCompat.addWebMessageListener(webView, "ddgGetAutofillConfig", setOf("*")) { wv, webMessage, uri, isMainFrame, reply ->
            Timber.w("cdr received a web message [ddgGetAutofillConfig] from JS layer, from main frame? $isMainFrame from url $uri")

            appCoroutineScope.launch(dispatchers.io()) {
                val url = withContext(dispatchers.main()) { wv.url }
                val config = autofillRuntimeConfigProvider.getRuntimeConfiguration(url)
                reply.postMessage(config)
                Timber.w("cdr sent reply to getAutofillConfig\n$config")
            }
        }
    }

    @SuppressLint("RequiresFeature")
    private fun addWebMessageListenerGetAutofillData(webView: WebView) {
        WebViewCompat.addWebMessageListener(webView, "ddgGetAutofillData", setOf("*")) { wv, webMessage, uri, isMainFrame, reply ->
            Timber.w("cdr received a web message [ddgGetAutofillData] from JS layer, from main frame? $isMainFrame from url $uri")
            messagePoster.messagePosterReplier = reply

            appCoroutineScope.launch(dispatchers.io()) {
                autofillInterface.getAutofillData(webMessage.data.toString())
            }
        }
    }

    @SuppressLint("RequiresFeature")
    private fun addWebMessageListenerGetInContextDismissedAt(webView: WebView) {
        WebViewCompat.addWebMessageListener(webView, "ddgGetIncontextSignupDismissedAt", setOf("*")) { wv, webMessage, uri, isMainFrame, replier ->
            Timber.w("cdr received a web message [ddgGetIncontextSignupDismissedAt] from JS layer, from main frame? $isMainFrame from url $uri")
            appCoroutineScope.launch(dispatchers.io()) {
                autofillInterface.getIncontextSignupDismissedAt(replier)
            }
        }
    }



    override fun removeJsInterface() {
        autofillInterface.webView = null
    }

    override fun injectCredentials(credentials: LoginCredentials?) {
        if (credentials == null) {
            autofillInterface.injectNoCredentials()
        } else {
            autofillInterface.injectCredentials(credentials)
        }
    }

    override fun cancelPendingAutofillRequestToChooseCredentials() {
        autofillInterface.cancelRetrievingStoredLogins()
    }

    override fun acceptGeneratedPassword() {
        autofillInterface.acceptGeneratedPassword()
    }

    override fun rejectGeneratedPassword() {
        autofillInterface.rejectGeneratedPassword()
    }

    override fun inContextEmailProtectionFlowFinished() {
        autofillInterface.inContextEmailProtectionFlowFinished()
    }
}
