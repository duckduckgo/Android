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
     * Called when user chooses to sign up for in-context email protection.
     */
    fun onSelectedToSignUpForInContextEmailProtection(autofillWebMessageRequest: AutofillWebMessageRequest)

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
}
