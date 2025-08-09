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
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.InternalAlwaysEnabled

/**
 * This is the class that represents the autofill feature flags
 */
interface AutofillFeature {
    /**
     * @return `true` when the remote config has the global "autofill" feature flag enabled
     * If the remote feature is not present defaults to `true`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle

    /**
     * Kill switch for if we should inject Autofill javascript into the browser.
     *
     * @return `true` when the remote config has the global "canIntegrateAutofillInWebView" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `true`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun canIntegrateAutofillInWebView(): Toggle

    /**
     * @return `true` when the remote config has the global "canInjectCredentials" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun canInjectCredentials(): Toggle

    /**
     * @return `true` when the remote config has the global "canSaveCredentials" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun canSaveCredentials(): Toggle

    /**
     * @return `true` when the remote config has the global "canGeneratePasswords" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun canGeneratePasswords(): Toggle

    /**
     * @return `true` when the remote config has the global "canAccessCredentialManagement" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun canAccessCredentialManagement(): Toggle

    /**
     * @return `true` when the remote config has the global "canCategorizeUnknownUsername" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun canCategorizeUnknownUsername(): Toggle

    /**
     * @return `true` when the remote config has "passwordVariantCategorization" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun passwordVariantCategorization(): Toggle

    /**
     * @return `true` when the remote config has the global "onByDefault" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun onByDefault(): Toggle

    /**
     * Remote Flag to control logic that decides if existing users that had autofill disabled by default, should have it enabled
     * @return `true` when the remote config has the global "onForExistingUsers" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    @InternalAlwaysEnabled
    fun onForExistingUsers(): Toggle

    /**
     * Remote Flag that enables the old dialog prompt to disable autofill
     * @return `true` when the remote config has the global "allowToDisableAutofill" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun showDisableDialogAutofillPrompt(): Toggle

    /**
     * Remote Flag that enables the ability to import passwords directly from Google Password Manager
     * @return `true` when the remote config has "canImportFromGooglePasswordManager" autofill sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @InternalAlwaysEnabled
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun canImportFromGooglePasswordManager(): Toggle

    /**
     * Remote flag that enables the ability to support partial form saves. A partial form save is common with scenarios like:
     *  - a multi-step login form where username and password are entered on separate pages
     *  - password reset flow
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun partialFormSaves(): Toggle

    /**
     * Kill switch for toggling how deep a domain comparison to do when saving/updating credentials.
     * We want to limit times we save duplicates, and we can do that with a deeper comparison of domains to decide if we already have a matching cred.
     * By deeper, this means comparing using E-TLD+1
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun deepDomainComparisonsOnExistingCredentialsChecks(): Toggle

    /**
     * Kill switch for making case insensitive checks on existing username matches
     */
    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
    fun ignoreCaseOnUsernameComparisons(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.FALSE)
    fun siteSpecificFixes(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
    fun settingsScreen(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
    fun createAsyncPreferences(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
    fun canPromoteImportPasswordsInPasswordManagement(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
    fun canPromoteImportGooglePasswordsInBrowser(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
    fun canShowImportOptionInAppSettings(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.INTERNAL)
    fun passkeySupport(): Toggle
}
