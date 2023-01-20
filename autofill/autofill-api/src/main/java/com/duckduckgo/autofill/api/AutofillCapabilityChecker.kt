/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.api

/**
 * Used to check the status of various Autofill features.
 *
 * Whether autofill features are enabled depends on a variety of inputs. This class provides a single way to query the status of all of them.
 */
interface AutofillCapabilityChecker {

    /**
     * Whether autofill is configured to be enabled. This is a configuration value, not a user preference.
     */
    suspend fun isAutofillEnabledByConfiguration(): Boolean

    /**
     * Whether autofill is enabled by the user. This is a user preference.
     */
    suspend fun isAutofillEnabledByUser(): Boolean

    /**
     * Whether secure autofill is available. This depends on the user's device capabilities and state.
     * Not all features of autofill require secure autofill to be available. Those that do can check this value.
     */
    suspend fun isSecureAutofillAvailable(): Boolean

    /**
     * Whether autofill can inject credentials into a WebView.
     */
    suspend fun canInjectCredentialsToWebView(): Boolean

    /**
     * Whether autofill can save credentials from a WebView.
     */
    suspend fun canSaveCredentialsFromWebView(): Boolean

    /**
     * Whether a user can access the credential management screen.
     */
    suspend fun canAccessCredentialManagementScreen(): Boolean
}
