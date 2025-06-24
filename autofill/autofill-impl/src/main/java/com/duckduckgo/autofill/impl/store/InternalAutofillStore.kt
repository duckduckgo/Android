/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.store

import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.store.AutofillStore
import kotlinx.coroutines.flow.Flow

interface InternalAutofillStore : AutofillStore {

    /**
     * Global toggle for determining / setting if autofill is enabled
     */
    var autofillEnabled: Boolean

    /**
     * Determines if the autofill feature is available for the user
     */
    suspend fun autofillAvailable(): Boolean

    /**
     * Used to determine if a user has ever been prompted to save a login (note: prompted to save, not necessarily saved)
     * Defaults to false, and will be set to true after the user has been shown a prompt to save a login
     */
    var hasEverBeenPromptedToSaveLogin: Boolean

    var hasEverImportedPasswords: Boolean

    var hasDeclinedInBrowserPasswordImportPromo: Boolean
    var hasDeclinedPasswordManagementImportPromo: Boolean

    fun hasEverImportedPasswordsFlow(): Flow<Boolean>

    var inBrowserImportPromoShownCount: Int

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
    suspend fun updateCredentials(
        rawUrl: String,
        credentials: LoginCredentials,
        updateType: CredentialUpdateExistingCredentialsDialog.CredentialUpdateType,
    ): LoginCredentials?

    /**
     * Returns the full list of stored login credentials
     */
    suspend fun getAllCredentials(): Flow<List<LoginCredentials>>

    /**
     * Returns a count of how many credentials are stored
     */
    suspend fun getCredentialCount(): Flow<Int>

    /**
     * Deletes all saved credentials
     */
    suspend fun deleteAllCredentials(): List<LoginCredentials>

    /**
     * Deletes the credential with the given ID
     * @return the deleted LoginCredentials, or null if the deletion couldn't be performed
     */
    suspend fun deleteCredentials(id: Long): LoginCredentials?

    /**
     * Updates the given login credentials, replacing what was saved before for the credentials with the specified ID
     * @param credentials The ID of the given credentials must match a saved credential for it to be updated.
     * @param refreshLastUpdatedTimestamp Whether to update the last-updated timestamp of the credential.
     * Defaults to true. Set to false if you don't want the last-updated timestamp modified.
     * @return The saved credential if it saved successfully, otherwise null
     */
    suspend fun updateCredentials(credentials: LoginCredentials, refreshLastUpdatedTimestamp: Boolean = true): LoginCredentials?

    /**
     * Used to reinsert a credential that was previously deleted
     * This supports the ability to give user a brief opportunity to 'undo' a deletion
     *
     * This is similar to a normal save, except it will preserve the original ID and last modified time
     */
    suspend fun reinsertCredentials(credentials: LoginCredentials)

    /**
     * Used to bulk insert credentials
     * @return The list of IDs of the inserted credentials
     */
    suspend fun bulkInsert(credentials: List<LoginCredentials>): List<Long>

    /**
     * Used to reinsert a list of credentials that were previously deleted
     * This supports the ability to give user a brief opportunity to 'undo' a mass deletion
     *
     * This is similar to a normal save, except it will preserve the original ID and last modified time
     */
    suspend fun reinsertCredentials(credentials: List<LoginCredentials>)

    /**
     * Searches the saved login credentials for a match to the given URL, username and password
     * This can be used to determine if we need to prompt the user to update a saved credential
     *
     * @return The match type, which might indicate there was an exact match, a partial match etc...
     */
    suspend fun containsCredentials(rawUrl: String, username: String?, password: String?): ContainsCredentialsResult
}
