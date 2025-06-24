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

package com.duckduckgo.autofill.api

import android.webkit.WebView
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType

/**
 * Public interface for accessing and configuring browser autofill functionality for a WebView instance
 */
interface BrowserAutofill {
    interface Configurator {
        /**
         * Configures autofill for the current webpage.
         * This should be called once per page load (e.g., onPageStarted())
         *
         * Responsible for injecting the required autofill configuration to the JS layer
         */
        fun configureAutofillForCurrentPage(
            webView: WebView,
            url: String?,
        )
    }

    /**
     * Adds the native->JS interface to the given WebView
     * This should be called once per WebView where autofill is to be available in it
     */
    fun addJsInterface(
        webView: WebView,
        autofillCallback: Callback,
        emailProtectionInContextCallback: EmailProtectionUserPromptListener? = null,
        emailProtectionInContextSignupFlowCallback: EmailProtectionInContextSignupFlowListener? = null,
        tabId: String,
    )

    /**
     * Removes the JS interface as a clean-up. Recommended to call from onDestroy() of Fragment/Activity containing the WebView
     */
    fun removeJsInterface()

    /**
     * Communicates with the JS layer to pass the given credentials
     *
     * @param credentials The credentials to be passed to the JS layer. Can be null to indicate credentials won't be autofilled.
     */
    fun injectCredentials(credentials: LoginCredentials?)

    /**
     * Cancels any ongoing autofill operations which would show the user the prompt to choose credentials
     * This would only normally be needed if a user-interaction happened such that showing autofill prompt would be undesirable.
     */
    fun cancelPendingAutofillRequestToChooseCredentials()

    /**
     * Informs the JS layer to use the generated password and fill it into the password field(s)
     */
    fun acceptGeneratedPassword()

    /**
     * Informs the JS layer not to use the generated password
     */
    fun rejectGeneratedPassword()

    /**
     * Informs the JS layer that the in-context Email Protection flow has finished
     */
    fun inContextEmailProtectionFlowFinished()

    /**
     * Informs the JS layer that new autofill data is available, and the JS flow to ask for credentials can start again.
     */
    fun onNewAutofillDataAvailable(url: String?)
}

/**
 * Callback for Email Protection prompts, signalling when to show the native UI to the user
 */
interface EmailProtectionUserPromptListener {

    /**
     * Called when the user should be shown prompt to sign up for Email Protection
     */
    fun showNativeInContextEmailProtectionSignupPrompt()

    /**
     * Called when the user should be shown prompt to choose an email address to use for email protection autofill
     */
    fun showNativeChooseEmailAddressPrompt()
}

/**
 * Callback for Email Protection events that might happen during the in-context signup flow
 */
interface EmailProtectionInContextSignupFlowListener {

    /**
     * Called when the in-context email protection signup flow should be closed
     */
    fun closeInContextSignup()
}

/**
 * Browser Autofill callbacks
 */
interface Callback {

    /**
     * Called when we've determined we have credentials we can offer to autofill for the user.
     * When this is called, we should present the list to the user for them to choose which one, if any, to autofill.
     */
    suspend fun onCredentialsAvailableToInject(
        originalUrl: String,
        credentials: List<LoginCredentials>,
        triggerType: LoginTriggerType,
    )

    /**
     * Called when there are login credentials available to be saved.
     * When this is called, we'd typically want to prompt the user if they want to save the credentials.
     */
    suspend fun onCredentialsAvailableToSave(
        currentUrl: String,
        credentials: LoginCredentials,
    )

    /**
     * Called when we've generated a password for the user, and we want to offer it to them to use.
     * When this is called, we should present the generated password to the user for them to choose whether to use it or not.
     */
    suspend fun onGeneratedPasswordAvailableToUse(
        originalUrl: String,
        username: String?,
        generatedPassword: String,
    )

    /**
     * Called when we've been asked which credentials we have available to autofill, but the answer is none.
     */
    fun noCredentialsAvailable(originalUrl: String)

    /**
     * Called when credentials have been saved, and we want to show the user some visual confirmation.
     */
    fun onCredentialsSaved(savedCredentials: LoginCredentials)

    suspend fun promptUserTo(event: AutofillPrompt)
}

sealed interface AutofillPrompt {
    data class ImportPasswords(val currentUrl: String) : AutofillPrompt
}
