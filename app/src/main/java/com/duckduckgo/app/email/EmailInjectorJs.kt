/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.email

import android.webkit.WebView
import androidx.annotation.UiThread
import com.duckduckgo.app.autofill.EmailProtectionJavascriptInjector
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.email.EmailJavascriptInterface.Companion.JAVASCRIPT_INTERFACE_NAME
import com.duckduckgo.autofill.api.Autofill
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.api.emailprotection.EmailInjector
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class EmailInjectorJs @Inject constructor(
    private val emailManager: EmailManager,
    private val urlDetector: DuckDuckGoUrlDetector,
    private val dispatcherProvider: DispatcherProvider,
    private val autofillFeature: AutofillFeature,
    private val emailProtectionJavascriptInjector: EmailProtectionJavascriptInjector,
    private val autofill: Autofill,
) : EmailInjector {

    override fun addJsInterface(
        webView: WebView,
        onSignedInEmailProtectionPromptShown: () -> Unit,
        onInContextEmailProtectionSignupPromptShown: () -> Unit,
    ) {
        // We always add the interface irrespectively if the feature is enabled or not
        webView.addJavascriptInterface(
            EmailJavascriptInterface(
                emailManager,
                webView,
                urlDetector,
                dispatcherProvider,
                autofillFeature,
                autofill,
                onSignedInEmailProtectionPromptShown,
            ),
            JAVASCRIPT_INTERFACE_NAME,
        )
    }

    @UiThread
    override fun injectAddressInEmailField(
        webView: WebView,
        alias: String?,
        url: String?,
    ) {
        url?.let {
            if (isFeatureEnabled() && !autofill.isAnException(url)) {
                webView.evaluateJavascript("javascript:${emailProtectionJavascriptInjector.getAliasFunctions(webView.context, alias)}", null)
            }
        }
    }

    @UiThread
    override fun notifyWebAppSignEvent(
        webView: WebView,
        url: String?,
    ) {
        url?.let {
            if (isFeatureEnabled() && isDuckDuckGoUrl(url) && !emailManager.isSignedIn()) {
                webView.evaluateJavascript("javascript:${emailProtectionJavascriptInjector.getSignOutFunctions(webView.context)}", null)
            }
        }
    }

    private fun isFeatureEnabled() = autofillFeature.self().isEnabled()

    private fun isDuckDuckGoUrl(url: String?): Boolean = (url != null && urlDetector.isDuckDuckGoEmailUrl(url))
}
