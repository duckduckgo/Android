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

import com.duckduckgo.feature.toggles.api.Toggle

/**
 * This is the class that represents the autofill feature flags
 */
interface AutofillFeature {
    /**
     * @return `true` when the remote config has the global "autofill" feature flag enabled
     * If the remote feature is not present defaults to `true`
     */
    @Toggle.DefaultValue(true)
    fun self(): Toggle

    /**
     * Kill switch for if we should inject Autofill javascript into the browser.
     *
     * @return `true` when the remote config has the global "canIntegrateAutofillInWebView" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `true`
     */
    @Toggle.DefaultValue(true)
    fun canIntegrateAutofillInWebView(): Toggle

    /**
     * @return `true` when the remote config has the global "canInjectCredentials" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(false)
    fun canInjectCredentials(): Toggle

    /**
     * @return `true` when the remote config has the global "canSaveCredentials" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(false)
    fun canSaveCredentials(): Toggle

    /**
     * @return `true` when the remote config has the global "canGeneratePasswords" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(false)
    fun canGeneratePasswords(): Toggle

    /**
     * @return `true` when the remote config has the global "canAccessCredentialManagement" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(false)
    fun canAccessCredentialManagement(): Toggle

    /**
     * @return `true` when the remote config has the global "onByDefault" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(false)
    fun onByDefault(): Toggle
}
