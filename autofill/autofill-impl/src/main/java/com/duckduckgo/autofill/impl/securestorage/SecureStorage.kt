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

package com.duckduckgo.autofill.impl.securestorage

import com.duckduckgo.autofill.impl.securestorage.encryption.EncryptionHelper.EncryptedString
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.store.SecureStorageRepository
import com.duckduckgo.securestorage.store.db.WebsiteLoginCredentialsEntity
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealSecureStorage @Inject constructor(
    private val secureStorageRepositoryFactory: SecureStorageRepository.Factory,
    private val dispatchers: DispatcherProvider,
    private val l2DataTransformer: L2DataTransformer,
) : SecureStorage {

    private val secureStorageRepository by lazy {
        secureStorageRepositoryFactory.get()
    }

    override fun canAccessSecureStorage(): Boolean = l2DataTransformer.canProcessData() && secureStorageRepository != null

    override suspend fun addWebsiteLoginDetailsWithCredentials(
        websiteLoginDetailsWithCredentials: WebsiteLoginDetailsWithCredentials,
    ): WebsiteLoginDetailsWithCredentials? {
        return withContext(dispatchers.io()) {
            val savedCredential = secureStorageRepository?.addWebsiteLoginCredential(websiteLoginDetailsWithCredentials.toDataEntity())
            return@withContext savedCredential?.toCredentials()
        }
    }

    override suspend fun websiteLoginDetailsForDomain(domain: String): Flow<List<WebsiteLoginDetails>> {
        return if (secureStorageRepository != null) {
            withContext(dispatchers.io()) {
                secureStorageRepository!!.websiteLoginCredentialsForDomain(domain).map { list ->
                    list.map {
                        it.toDetails()
                    }
                }
            }
        } else {
            emptyFlow()
        }
    }

    override suspend fun websiteLoginDetails(): Flow<List<WebsiteLoginDetails>> =
        if (secureStorageRepository != null) {
            withContext(dispatchers.io()) {
                secureStorageRepository!!.websiteLoginCredentials().map { list ->
                    list.map {
                        it.toDetails()
                    }
                }
            }
        } else {
            emptyFlow()
        }

    override suspend fun getWebsiteLoginDetailsWithCredentials(id: Long): WebsiteLoginDetailsWithCredentials? =
        withContext(dispatchers.io()) {
            secureStorageRepository?.getWebsiteLoginCredentialsForId(id)?.toCredentials()
        }

    override suspend fun websiteLoginDetailsWithCredentialsForDomain(domain: String): Flow<List<WebsiteLoginDetailsWithCredentials>> =
        if (secureStorageRepository != null) {
            withContext(dispatchers.io()) {
                secureStorageRepository!!.websiteLoginCredentialsForDomain(domain).map { list ->
                    list.map {
                        it.toCredentials()
                    }
                }
            }
        } else {
            flowOf(emptyList())
        }

    override suspend fun websiteLoginDetailsWithCredentials(): Flow<List<WebsiteLoginDetailsWithCredentials>> =
        if (secureStorageRepository != null) {
            withContext(dispatchers.io()) {
                secureStorageRepository!!.websiteLoginCredentials().map { list ->
                    list.map {
                        it.toCredentials()
                    }
                }
            }
        } else {
            flowOf(emptyList())
        }

    override suspend fun updateWebsiteLoginDetailsWithCredentials(
        websiteLoginDetailsWithCredentials: WebsiteLoginDetailsWithCredentials,
    ): WebsiteLoginDetailsWithCredentials? =
        withContext(dispatchers.io()) {
            secureStorageRepository?.updateWebsiteLoginCredentials(websiteLoginDetailsWithCredentials.toDataEntity())?.toCredentials()
        }

    override suspend fun deleteWebsiteLoginDetailsWithCredentials(id: Long): Unit =
        withContext(dispatchers.io()) {
            secureStorageRepository?.deleteWebsiteLoginCredentials(id)
        }

    private fun WebsiteLoginDetailsWithCredentials.toDataEntity(): WebsiteLoginCredentialsEntity {
        val encryptedPassword = encryptData(password)
        val encryptedNotes = encryptData(notes)
        return WebsiteLoginCredentialsEntity(
            id = details.id ?: 0,
            domain = details.domain,
            username = details.username,
            password = encryptedPassword?.data,
            passwordIv = encryptedPassword?.iv,
            notes = encryptedNotes?.data,
            notesIv = encryptedNotes?.iv,
            domainTitle = details.domainTitle,
            lastUpdatedInMillis = details.lastUpdatedMillis,
        )
    }

    private fun WebsiteLoginCredentialsEntity.toCredentials(): WebsiteLoginDetailsWithCredentials =
        WebsiteLoginDetailsWithCredentials(
            details = toDetails(),
            password = decryptData(password, passwordIv),
            notes = decryptData(notes, notesIv),
        )

    private fun WebsiteLoginCredentialsEntity.toDetails(): WebsiteLoginDetails =
        WebsiteLoginDetails(
            domain = domain,
            username = username,
            id = id,
            domainTitle = domainTitle,
            lastUpdatedMillis = lastUpdatedInMillis,
        )

    // only encrypt when there's data
    private fun encryptData(data: String?): EncryptedString? = data?.let { l2DataTransformer.encrypt(it) }

    private fun decryptData(
        data: String?,
        iv: String?,
    ): String? {
        // only decrypt when there's data and iv
        return data?.let { _data ->
            iv?.let { _iv ->
                l2DataTransformer.decrypt(_data, _iv)
            }
        }
    }
}
