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

package com.duckduckgo.autofill.store

import com.duckduckgo.autofill.domain.app.LoginCredentials
import kotlinx.coroutines.flow.Flow

/**
 * APIs for accessing and updating saved autofill data
 */
interface AutofillStore {

    /**
     * Global toggle for determining / setting if autofill is enabled
     */
    var autofillEnabled: Boolean

    /**
     * Used to determine whether we show additional onboarding info when offering to save a login credential
     *
     * This will default to true, and remain true until after the first credential has been saved
     */
    var showOnboardingWhenOfferingToSaveLogin: Boolean

    /**
     * Find saved credentials for the given URL, returning an empty list where no matches are found
     * @param rawUrl Can be a full, unmodified URL taken from the URL bar (containing subdomains, query params etc...)
     */
    suspend fun getCredentials(rawUrl: String): List<LoginCredentials>

    /**
     * Save the given credentials for the given URL
     * @param rawUrl Can be a full, unmodified URL taken from the URL bar (containing subdomains, query params etc...)
     * @param credentials The credentials to be saved. The ID can be null.
     */
    suspend fun saveCredentials(rawUrl: String, credentials: LoginCredentials)

    /**
     * Updates the credentials saved for the given URL
     * @param rawUrl Can be a full, unmodified URL taken from the URL bar (containing subdomains, query params etc...)
     * @param credentials The credentials to be updated. The ID can be null.
     */
    suspend fun updateCredentials(rawUrl: String, credentials: LoginCredentials)

    /**
     * Returns the full list of stored login credentials
     */
    suspend fun getAllCredentials(): Flow<List<LoginCredentials>>

    /**
     * Deletes the credential with the given ID
     */
    suspend fun deleteCredentials(id: Int)

    /**
     * Updates the given login credentials, replacing what was saved before for the credentials with the specified ID
     * @param credentials The ID of the given credentials must match a saved credential for it to be updated.
     */
    suspend fun updateCredentials(credentials: LoginCredentials)

    /**
     * Searches the saved login credentials for a match to the given URL, username and password
     * This can be used to determine if we need to prompt the user to update a saved credential
     *
     * @return The match type, which might indicate there was an exact match, a partial match etc...
     */
    suspend fun containsCredentials(rawUrl: String, username: String?, password: String?): ContainsCredentialsResult

    /**
     * Possible match types returned when searching for the presence of credentials
     */
    sealed interface ContainsCredentialsResult {
        object ExactMatch : ContainsCredentialsResult
        object UsernameMatch : ContainsCredentialsResult
        object UrlOnlyMatch : ContainsCredentialsResult
        object NoMatch : ContainsCredentialsResult
    }
}
