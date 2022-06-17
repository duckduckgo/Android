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
     * @return `true` if all dependencies of SecureStorage have been instantiated properly otherwise `false
     */
    fun canAccessSecureStorage(): Boolean

    /**
     * This method authenticates the user without having to pass a user password. This method is relevant
     * while the user password generation is still done programmatically within the secure storage. Once we support
     * password creation, this method will be deprecated.
     *
     * @return Result.Success if no issues with authentication is encountered otherwise Result.Error
     */
    suspend fun authenticateUser(): Result

    /**
     * This method authenticates the user using a provided [password].
     *
     * @return Result.Success if no issues with authentication is encountered otherwise Result.Error
     */
    suspend fun authenticateUser(password: String): Result

    /**
     * This method adds a raw plaintext [WebsiteLoginCredentials] into the [SecureStorage]. This requires the user to be authenticated
     * first see [authenticateUser].
     *
     * @throws [SecureStorageException] if something went wrong while trying to perform the action. See type to get more info on the cause.
     */
    @Throws(SecureStorageException::class)
    suspend fun addWebsiteLoginCredential(websiteLoginCredentials: WebsiteLoginCredentials)

    /**
     * This method returns all [WebsiteLoginDetails] with the [domain] stored in the [SecureStorage].
     * This does not require the user to be authenticated since [WebsiteLoginDetails] doesn't contain any L2 data.
     *
     * @return Flow<List<WebsiteLoginDetails>> a flow emitting a List of plain text WebsiteLoginDetails stored in SecureStorage.
     */
    suspend fun websiteLoginDetailsForDomain(domain: String): Flow<List<WebsiteLoginDetails>>

    /**
     * This method returns all [WebsiteLoginDetails] stored in the [SecureStorage].
     * This does not require the user to be authenticated since [WebsiteLoginDetails] doesn't contain any L2 data.
     *
     * @return Flow<List<WebsiteLoginDetails>> a flow containing a List of plain text WebsiteLoginDetails stored in SecureStorage.
     */
    suspend fun websiteLoginDetails(): Flow<List<WebsiteLoginDetails>>

    /**
     * This method returns the [WebsiteLoginCredentials] with the [id] stored in the [SecureStorage].
     * This requires the user to be authenticated.
     *
     * @return [WebsiteLoginCredentials] containing the plaintext password
     * @throws [SecureStorageException] if something went wrong while trying to perform the action. See type to get more info on the cause.
     */
    @Throws(SecureStorageException::class)
    suspend fun getWebsiteLoginCredentials(id: Int): WebsiteLoginCredentials

    /**
     * This method returns the [WebsiteLoginCredentials] with the [domain] stored in the [SecureStorage].
     * This requires the user to be authenticated.
     *
     * @return Flow<List<WebsiteLoginCredentials>>  a flow emitting a List of plain text WebsiteLoginCredentials stored in SecureStorage
     * containing the plaintext password
     * @throws [SecureStorageException] if something went wrong while trying to perform the action. See type to get more info on the cause.
     */
    @Throws(SecureStorageException::class)
    suspend fun websiteLoginCredentialsForDomain(domain: String): Flow<List<WebsiteLoginCredentials>>

    /**
     * This method returns all the [WebsiteLoginCredentials] stored in the [SecureStorage].
     * This requires the user to be authenticated.
     *
     * @return Flow<List<WebsiteLoginCredentials>>  a flow emitting a List of plain text WebsiteLoginCredentials stored in SecureStorage
     * containing the plaintext password
     * @throws [SecureStorageException] if something went wrong while trying to perform the action. See type to get more info on the cause.
     */
    @Throws(SecureStorageException::class)
    suspend fun websiteLoginCredentials(): Flow<List<WebsiteLoginCredentials>>

    /**
     * This method updates an existing [WebsiteLoginCredentials] in the [SecureStorage].
     * This requires the user to be authenticated.
     *
     * @throws [SecureStorageException] if something went wrong while trying to perform the action. See type to get more info on the cause.
     */
    @Throws(SecureStorageException::class)
    suspend fun updateWebsiteLoginCredentials(websiteLoginCredentials: WebsiteLoginCredentials)

    /**
     * This method removes an existing [WebsiteLoginCredentials] associated with an [id] from the [SecureStorage].
     * This does not require the user to be authenticated.
     */
    suspend fun deleteWebsiteLoginCredentials(id: Int)
}
