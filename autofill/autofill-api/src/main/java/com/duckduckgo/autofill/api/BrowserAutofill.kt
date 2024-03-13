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

import android.os.Parcelable
import android.webkit.WebView
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType
import kotlinx.parcelize.Parcelize

/**
 * Public interface for accessing and configuring browser autofill functionality for a WebView instance
 */
interface BrowserAutofill {

    /**
     * Adds the native->JS interface to the given WebView
     * This should be called once per WebView where autofill is to be available in it
     */
    suspend fun addJsInterface(
        webView: WebView,
        autofillCallback: Callback,
        tabId: String,
    )

    /**
     * Removes the JS interface as a clean-up. Recommended to call from onDestroy() of Fragment/Activity containing the WebView
     */
    fun removeJsInterface(webView: WebView?)

    /**
     * Notifies that there has been a change in web page, and the autofill state should be re-evaluated
     */
    fun notifyPageChanged()

    /**
     * Cancels any ongoing autofill operations which would show the user the prompt to choose credentials
     * This would only normally be needed if a user-interaction happened such that showing autofill prompt would be undesirable.
     */
    fun cancelPendingAutofillRequestToChooseCredentials()
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
        autofillWebMessageRequest: AutofillWebMessageRequest,
        credentials: List<LoginCredentials>,
        triggerType: LoginTriggerType,
    )

    /**
     * Called when there are login credentials available to be saved.
     * When this is called, we'd typically want to prompt the user if they want to save the credentials.
     */
    suspend fun onCredentialsAvailableToSave(
        autofillWebMessageRequest: AutofillWebMessageRequest,
        credentials: LoginCredentials,
    )

    /**
     * Called when we've generated a password for the user, and we want to offer it to them to use.
     * When this is called, we should present the generated password to the user for them to choose whether to use it or not.
     */
    suspend fun onGeneratedPasswordAvailableToUse(
        autofillWebMessageRequest: AutofillWebMessageRequest,
        username: String?,
        generatedPassword: String,
    )

    /**
     * Called when the user should be shown prompt to choose an email address to use for email protection autofill
     */
    fun showNativeChooseEmailAddressPrompt(autofillWebMessageRequest: AutofillWebMessageRequest)

    /**
     * Called when the user should be shown prompt to sign up for Email Protection
     */
    fun showNativeInContextEmailProtectionSignupPrompt(autofillWebMessageRequest: AutofillWebMessageRequest)

    /**
     * Called when credentials have been saved, and we want to show the user some visual confirmation.
     */
    fun onCredentialsSaved(savedCredentials: LoginCredentials)
}

/**
 * When there is an autofill request to be handled that requires user-interaction, we need to know where the request came from when later responding
 *
 * This is metadata about the WebMessage request that was received from the JS.
 */
@Parcelize
data class AutofillWebMessageRequest(
    /**
     * The origin of the request. Note, this may be a different origin than the page the user is currently on if the request came from an iframe
     */
    val requestOrigin: String,

    /**
     * The user-facing URL of the page where the autofill request originated
     */
    val originalPageUrl: String?,

    /**
     * The ID of the original request from the JS. This request ID is required in order to later provide a response using the web message reply API
     */
    val requestId: String,
) : Parcelable
