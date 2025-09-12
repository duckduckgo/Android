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

package com.duckduckgo.autofill.impl

import com.duckduckgo.autofill.api.Callback
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillInputSubType

/**
 * Internal extension of the public Callback interface for extensions that isn't required outside of this module
 */
interface InternalCallback : Callback {

    /**
     * Called when we've determined we have credentials we can offer to autofill for the user,
     * with additional re-authentication context.
     * * @param originalUrl The URL where autofill was requested
     * @param credentials List of available stored credentials for this URL
     * @param triggerType How this autofill request was triggered
     * @param requestSubType The type of autofill request (USERNAME or PASSWORD)
     */
    suspend fun onCredentialsAvailableToInjectWithReauth(
        originalUrl: String,
        credentials: List<LoginCredentials>,
        triggerType: LoginTriggerType,
        requestSubType: SupportedAutofillInputSubType,
    ) {
        // Default implementation delegates to the public API
        onCredentialsAvailableToInject(originalUrl, credentials, triggerType)
    }
}
