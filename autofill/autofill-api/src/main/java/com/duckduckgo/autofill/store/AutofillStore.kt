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

import com.duckduckgo.autofill.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType
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
     * Determines if the autofill feature is available for the user
     */
    val autofillAvailable: Boolean

    /**
     * Used to determine whether we show additional onboarding info when offering to save a login credential
     *
     * This will default to true, and remain true until after the first credential has been saved
     */
    var showOnboardingWhenOfferingToSaveLogin: Boolean

    /**
     * Whether to monitor autofill decline counts or not
     * Used to determine whether we should actively detect when a user new to autofill doesn't appear to want it enabled
     */
    var monitorDeclineCounts: Boolean

    /**
     * A count of the number of autofill declines the user has made, persisted across all sessions.
     * Used to determine whether we should prompt a user new to autofill to disable it if they don't appear to want it enabled
     */
    var autofillDeclineCount: Int

    /**
     * Find saved credentials for the given URL, returning an empty list where no matches are found
     * @param rawUrl Can be a full, unmodified URL taken from the URL bar (containing subdomains, query params etc...)
     */
    suspend fun getCredentials(rawUrl: String): List<LoginCredentials>

    /**
     * Find saved credential for the given id
     * @param id of the saved credential
     */
    suspend fun getCredentialsWithId(id: Long): LoginCredentials?

    /**
     * Save the given credentials for the given URL
     * @param rawUrl Can be a full, unmodified URL taken from the URL bar (containing subdomains, query params etc...)
     * @param credentials The credentials to be saved. The ID can be null.
     * @return The saved credential if it saved successfully, otherwise null
     */
    suspend fun saveCredentials(rawUrl: String, credentials: LoginCredentials): LoginCredentials?

    /**
     * Updates the credentials saved for the given URL
     * @param rawUrl Can be a full, unmodified URL taken from the URL bar (containing subdomains, query params etc...)
     * @param credentials The credentials to be updated. The ID can be null.
     * @param updateType The type of update to perform, whether updating the username or password.
     * @return The saved credential if it saved successfully, otherwise null
     */
    suspend fun updateCredentials(rawUrl: String, credentials: LoginCredentials, updateType: CredentialUpdateType): LoginCredentials?

    /**
     * Returns the full list of stored login credentials
     */
    suspend fun getAllCredentials(): Flow<List<LoginCredentials>>

    /**
     * Deletes the credential with the given ID
     */
    suspend fun deleteCredentials(id: Long)

    /**
     * Updates the given login credentials, replacing what was saved before for the credentials with the specified ID
     * @param credentials The ID of the given credentials must match a saved credential for it to be updated.
     * @return The saved credential if it saved successfully, otherwise null
     */
    suspend fun updateCredentials(credentials: LoginCredentials): LoginCredentials?

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
        object UsernameMissing : ContainsCredentialsResult
        object NoMatch : ContainsCredentialsResult
    }
}
