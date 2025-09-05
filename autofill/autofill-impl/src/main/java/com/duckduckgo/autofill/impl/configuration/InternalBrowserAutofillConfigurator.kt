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

package com.duckduckgo.autofill.impl.configuration

import android.webkit.WebView
import com.duckduckgo.autofill.api.BrowserAutofill.Configurator
import com.duckduckgo.autofill.impl.store.ReAuthenticationDetails

interface InternalBrowserAutofillConfigurator : Configurator {
    /**
     * Configures autofill for the current webpage with optional automatic re-authentication support.
     * This should be called once per page load (e.g., onPageStarted())
     *
     * @param webView The WebView to configure
     * @param url The URL of the current page
     * @param reauthenticationDetails Whether to enable automatic re-authentication for this page
     */
    fun configureAutofillForCurrentPage(
        webView: WebView,
        url: String?,
        reauthenticationDetails: ReAuthenticationDetails,
    )
}
