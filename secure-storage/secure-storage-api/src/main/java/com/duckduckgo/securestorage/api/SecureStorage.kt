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

package com.duckduckgo.securestorage.api

import kotlinx.coroutines.flow.Flow

/** Public API for the secure storage feature */
interface SecureStorage {

    /**
     * This method can be used to check if the secure storage has been instantiated properly.
     *
     * @return `true` if all dependencies of SecureStorage have been instantiated properly otherwise `false`
     */
    fun canAccessSecureStorage(): Boolean

    /**
     * This method adds a raw plaintext [WebsiteLoginDetailsWithCredentials] into the [SecureStorage]. If [canAccessSecureStorage] is false when
     * this is invoked, nothing will be done.
     *
     * @throws [SecureStorageException] if something went wrong while trying to perform the action. See type to get more info on the cause.
     * @return The saved credential if it saved successfully, otherwise null
     */
    @Throws(SecureStorageException::class)
    suspend fun addWebsiteLoginDetailsWithCredentials(
        websiteLoginDetailsWithCredentials: WebsiteLoginDetailsWithCredentials,
    ): WebsiteLoginDetailsWithCredentials?

    /**
     * This method returns all [WebsiteLoginDetails] with the [domain] stored in the [SecureStorage].
     * Only L1 encrypted data is returned by these function. This is best use when the need is only to access non-sensitive data.
     * If [canAccessSecureStorage] is false when this is invoked, an empty flow will be emitted.
     *
     * @return Flow<List<WebsiteLoginDetails>> a flow emitting a List of plain text WebsiteLoginDetails stored in SecureStorage.
     */
    suspend fun websiteLoginDetailsForDomain(domain: String): Flow<List<WebsiteLoginDetails>>

    /**
     * This method returns all [WebsiteLoginDetails] stored in the [SecureStorage].
     * Only L1 encrypted data is returned by these function. This is best use when the need is only to access non-sensitive data.
     * If [canAccessSecureStorage] is false when this is invoked, an empty flow will be emitted.
     *
     * @return Flow<List<WebsiteLoginDetails>> a flow containing a List of plain text WebsiteLoginDetails stored in SecureStorage.
     */
    suspend fun websiteLoginDetails(): Flow<List<WebsiteLoginDetails>>

    /**
     * This method returns the [WebsiteLoginDetailsWithCredentials] with the [id] stored in the [SecureStorage].
     * This returns decrypted sensitive data (encrypted in L2). Use this only when sensitive data is needed to be accessed.
     * If [canAccessSecureStorage] is false when this is invoked, null will be returned.
     *
     * @return [WebsiteLoginDetailsWithCredentials] containing the plaintext password
     * @throws [SecureStorageException] if something went wrong while trying to perform the action. See type to get more info on the cause.
     */
    @Throws(SecureStorageException::class)
    suspend fun getWebsiteLoginDetailsWithCredentials(id: Long): WebsiteLoginDetailsWithCredentials?

    /**
     * This method returns the [WebsiteLoginDetailsWithCredentials] with the [domain] stored in the [SecureStorage].
     * This returns decrypted sensitive data (encrypted in L2). Use this only when sensitive data is needed to be accessed.
     * If [canAccessSecureStorage] is false when this is invoked, an empty flow will be emitted.
     *
     * @return Flow<List<WebsiteLoginDetailsWithCredentials>> a flow emitting a List of plain text WebsiteLoginDetailsWithCredentials stored
     * in SecureStorage containing the plaintext password
     * @throws [SecureStorageException] if something went wrong while trying to perform the action. See type to get more info on the cause.
     */
    @Throws(SecureStorageException::class)
    suspend fun websiteLoginDetailsWithCredentialsForDomain(domain: String): Flow<List<WebsiteLoginDetailsWithCredentials>>

    /**
     * This method returns all the [WebsiteLoginDetailsWithCredentials] stored in the [SecureStorage].
     * This returns decrypted sensitive data (encrypted in L2). Use this only when sensitive data is needed to be accessed.
     * If [canAccessSecureStorage] is false when this is invoked, an empty flow will be emitted.
     *
     * @return Flow<List<WebsiteLoginDetailsWithCredentials>>  a flow emitting a List of plain text WebsiteLoginDetailsWithCredentials stored
     * in SecureStorage containing the plaintext password
     * @throws [SecureStorageException] if something went wrong while trying to perform the action. See type to get more info on the cause.
     */
    @Throws(SecureStorageException::class)
    suspend fun websiteLoginDetailsWithCredentials(): Flow<List<WebsiteLoginDetailsWithCredentials>>

    /**
     * This method returns all the [WebsiteLoginDetailsWithCredentials] stored in the [SecureStorage] modified [since].
     * This returns decrypted sensitive data (encrypted in L2). Use this only when sensitive data is needed to be accessed.
     * If [canAccessSecureStorage] is false when this is invoked, an empty flow will be emitted.
     *
     * @param [since] Time in milliseconds indicating when the credential was last updated, used for filtering.
     * @return List<WebsiteLoginDetailsWithCredentials> list of plain text WebsiteLoginDetailsWithCredentials stored
     * in SecureStorage containing the plaintext password
     * @throws [SecureStorageException] if something went wrong while trying to perform the action. See type to get more info on the cause.
     */
    @Throws(SecureStorageException::class)
    suspend fun websiteLoginDetailsWithCredentialsModifiedSince(since: Long): List<WebsiteLoginDetailsWithCredentials>

    /**
     * This method updates an existing [WebsiteLoginDetailsWithCredentials] in the [SecureStorage].
     * If [canAccessSecureStorage] is false when this is invoked, nothing will be done.
     *
     * @throws [SecureStorageException] if something went wrong while trying to perform the action. See type to get more info on the cause.
     * @return The updated credential if it saved successfully, otherwise null
     */
    @Throws(SecureStorageException::class)
    suspend fun updateWebsiteLoginDetailsWithCredentials(
        websiteLoginDetailsWithCredentials: WebsiteLoginDetailsWithCredentials,
    ): WebsiteLoginDetailsWithCredentials?

    /**
     * This method removes an existing [WebsiteLoginDetailsWithCredentials] associated with an [id] from the [SecureStorage].
     * If [canAccessSecureStorage] is false when this is invoked, nothing will be done.
     */
    suspend fun deleteWebsiteLoginDetailsWithCredentials(id: Long)
}
