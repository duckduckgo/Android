/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.webauthn

import android.annotation.SuppressLint
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewFeature.WEB_AUTHENTICATION
import com.duckduckgo.app.browser.DuckDuckGoWebView
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

interface WebViewPasskeyInitializer {
    suspend fun configurePasskeySupport(webView: DuckDuckGoWebView)
}

@ContributesBinding(AppScope::class)
class RealWebViewPasskeyInitializer @Inject constructor(
    private val autofillFeature: AutofillFeature,
    private val dispatchers: DispatcherProvider,
) : WebViewPasskeyInitializer {

    override suspend fun configurePasskeySupport(webView: DuckDuckGoWebView) {
        if (featureFlagEnabled() && webViewCapable()) {
            enablePasskeySupport(webView)
        }
    }

    @SuppressLint("RequiresFeature")
    private suspend fun enablePasskeySupport(webView: DuckDuckGoWebView) {
        withContext(dispatchers.main()) {
            if (!webView.isDestroyed()) {
                WebSettingsCompat.setWebAuthenticationSupport(webView.settings, WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER)
                logcat { "Autofill-passkey: WebView passkey support (WebAuthn) enabled" }
            }
        }
    }

    private suspend fun featureFlagEnabled(): Boolean {
        return withContext(dispatchers.io()) {
            autofillFeature.passkeySupport().isEnabled()
        }
    }

    private suspend fun webViewCapable(): Boolean {
        return withContext(dispatchers.main()) {
            WebViewFeature.isFeatureSupported(WEB_AUTHENTICATION)
        }
    }
}
