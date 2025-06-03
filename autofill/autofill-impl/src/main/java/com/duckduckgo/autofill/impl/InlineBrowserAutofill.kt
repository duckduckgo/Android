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

import android.webkit.WebView
import com.duckduckgo.autofill.api.BrowserAutofill
import com.duckduckgo.autofill.api.Callback
import com.duckduckgo.autofill.api.EmailProtectionInContextSignupFlowListener
import com.duckduckgo.autofill.api.EmailProtectionUserPromptListener
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.passwordgeneration.AutomaticSavedLoginsMonitor
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.LogPriority.VERBOSE
import logcat.logcat

@ContributesBinding(FragmentScope::class)
class InlineBrowserAutofill @Inject constructor(
    private val autofillInterface: AutofillJavascriptInterface,
    private val autoSavedLoginsMonitor: AutomaticSavedLoginsMonitor,
) : BrowserAutofill {

    override fun addJsInterface(
        webView: WebView,
        autofillCallback: Callback,
        emailProtectionInContextCallback: EmailProtectionUserPromptListener?,
        emailProtectionInContextSignupFlowCallback: EmailProtectionInContextSignupFlowListener?,
        tabId: String,
    ) {
        logcat(VERBOSE) { "Injecting BrowserAutofill interface" }
        // Adding the interface regardless if the feature is available or not
        webView.addJavascriptInterface(autofillInterface, AutofillJavascriptInterface.INTERFACE_NAME)
        autofillInterface.webView = webView
        autofillInterface.callback = autofillCallback
        autofillInterface.emailProtectionInContextCallback = emailProtectionInContextCallback
        autofillInterface.autoSavedLoginsMonitor = autoSavedLoginsMonitor
        autofillInterface.tabId = tabId
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
