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

package com.duckduckgo.autofill.impl.passkey

import android.webkit.WebView
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.PasskeyDetection
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealPassKeyDetection @Inject constructor(
    private val passkeyInterface: PasskeyDetectionJavascriptInterface,
    private val dispatchers: DispatcherProvider,
    private val webViewCompatWrapper: WebViewCompatWrapper,
    private val autofillFeature: AutofillFeature,
) : PasskeyDetection {

    override suspend fun addJsInterface(webView: WebView) {
        withContext(dispatchers.main()) {
            webView.addJavascriptInterface(passkeyInterface, passkeyInterface.name())
            val script = withContext(dispatchers.io()) {
                if (!autofillFeature.passkeySupport().isEnabled()) return@withContext null
                """
                    (function() {
                        if (!navigator.credentials) {
                            console.log('navigator.credentials not available');
                            return;
                        }

                        const originalGet = navigator.credentials.get.bind(navigator.credentials);
                        const originalCreate = navigator.credentials.create.bind(navigator.credentials);

                        navigator.credentials.get = function(options) {
                            if (window.PasskeyDetection) {
                                window.PasskeyDetection.passkeyAuthenticationStarted();
                            }

                            const promise = originalGet(options);

                            return promise.then(credential => {
                                if (window.PasskeyDetection) {
                                    window.PasskeyDetection.passkeyAuthenticationSucceeded();
                                }
                                return credential;
                            }).catch(error => {
                                if (window.PasskeyDetection) {
                                    window.PasskeyDetection.passkeyAuthenticationFailed(error.name, error.message);
                                }
                                throw error;
                            });
                        };

                        navigator.credentials.create = function(options) {
                            if (window.PasskeyDetection) {
                                window.PasskeyDetection.passkeyRegistrationStarted();
                            }

                            const promise = originalCreate(options);

                            return promise.then(credential => {
                                if (window.PasskeyDetection) {
                                    window.PasskeyDetection.passkeyRegistrationSucceeded();
                                }
                                return credential;
                            }).catch(error => {
                                if (window.PasskeyDetection) {
                                    window.PasskeyDetection.passkeyRegistrationFailed(error.name, error.message);
                                }
                                throw error;
                            });
                        };
                        console.log('Passkey monitoring initialized');
                    })();
                """.trimIndent()
            }
            script?.let {
                webViewCompatWrapper.addDocumentStartJavaScript(webView, script, setOf("*"))
            }
        }
    }
}
