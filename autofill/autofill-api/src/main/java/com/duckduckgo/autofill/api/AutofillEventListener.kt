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

import androidx.annotation.MainThread
import com.duckduckgo.autofill.api.domain.app.LoginCredentials

/**
 * Autofill event listener interface. Used to communicate key events from Autofill back to the app.
 */
@MainThread
interface AutofillEventListener {

    /**
     * Called when user chooses to use a generated password when prompted.
     * @param originalUrl the URL of the page that prompted the user to use a generated password
     */
    fun onAcceptGeneratedPassword(originalUrl: String)

    /**
     * Called when user chooses not to use a generated password when prompted.
     * @param originalUrl the URL of the page that prompted the user to use a generated password
     */
    fun onRejectGeneratedPassword(originalUrl: String)

    /**
     * Called when user chooses to autofill their personal duck address.
     * @param originalUrl the URL of the page that prompted the user to use their personal duck address
     * @param duckAddress the personal duck address that the user chose to autofill
     */
    fun onUseEmailProtectionPersonalAddress(originalUrl: String, duckAddress: String)

    /**
     * Called when user chooses to autofill a private duck address (private alias).
     * @param originalUrl the URL of the page that prompted the user to use a private duck address
     * @param duckAddress the private duck address that the user chose to autofill
     */
    fun onUseEmailProtectionPrivateAlias(originalUrl: String, duckAddress: String)

    /**
     * Called when user chooses to sign up for in-context email protection.
     */
    fun onSelectedToSignUpForInContextEmailProtection()

    /**
     * Called when the Email Protection in-context flow ends, for any reason
     */
    fun onEndOfEmailProtectionInContextSignupFlow()

    /**
     * Called when user chooses to autofill a login credential to a web page.
     * @param originalUrl the URL of the page that prompted the user to use a login credential
     * @param selectedCredentials the login credential that the user chose to autofill
     */
    fun onShareCredentialsForAutofill(originalUrl: String, selectedCredentials: LoginCredentials)

    /**
     * Called when user chooses not to autofill any login credential to a web page.
     * @param originalUrl the URL of the page that prompted the user to use a login credential
     */
    fun onNoCredentialsChosenForAutofill(originalUrl: String)

    /**
     * Called when a login credential was saved. This API could be used to show visual confirmation to the user.
     * @param credentials the login credential that was saved
     */
    fun onSavedCredentials(credentials: LoginCredentials)

    /**
     * Called when a login credential was updated. This API could be used to show visual confirmation to the user.
     * @param credentials the login credential that were updated
     */
    fun onUpdatedCredentials(credentials: LoginCredentials)

    /**
     * Called when a change was detected in the autofill state, such that reloading the page may be necessary.
     */
    fun onAutofillStateChange()

    /**
     * Called when new passwords are imported.
     * This can be used to trigger the autofill js flow to re-fetch credentials.
     */
    fun onNewPasswordsImported()
}
