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

import com.duckduckgo.autofill.impl.securestorage.encryption.EncryptionHelper
import com.duckduckgo.autofill.impl.securestorage.encryption.EncryptionHelper.EncryptedBytes
import com.duckduckgo.autofill.store.SecureStorageKeyRepository
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.impl.encryption.RandomBytesGenerator
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.toByteString
import java.security.Key
import javax.inject.Inject

/**
 * This class provides the usable decrypted keys to be used in various levels on encryption
 */
interface SecureStorageKeyProvider {
    suspend fun canAccessKeyStore(): Boolean

    /**
     * Ready to use key for L1 encryption
     */
    suspend fun getl1Key(): ByteArray

    /**
     * Ready to use key for L2 encryption using the generated user password
     */
    suspend fun getl2Key(): Key
}

@ContributesBinding(AppScope::class)
class RealSecureStorageKeyProvider @Inject constructor(
    private val randomBytesGenerator: RandomBytesGenerator,
    private val secureStorageKeyRepository: SecureStorageKeyRepository,
    private val encryptionHelper: EncryptionHelper,
    private val secureStorageKeyGenerator: SecureStorageKeyGenerator,
) : SecureStorageKeyProvider {

    override suspend fun canAccessKeyStore(): Boolean = secureStorageKeyRepository.canUseEncryption()
    private val l1KeyMutex = Mutex()
    private val l2KeyMutex = Mutex()

    override suspend fun getl1Key(): ByteArray {
        l1KeyMutex.withLock {
            // If no key exists in the keystore, we generate a new one and store it
            secureStorageKeyRepository.getL1Key()?.let {
                return it
            }
            val newKey = randomBytesGenerator.generateBytes(L1_PASSPHRASE_SIZE)
            return try {
                secureStorageKeyRepository.setL1Key(newKey)
                newKey
            } catch (e: SecureStorageException.KeyAlreadyExistsException) {
                // Another process wrote the key between our read and write — use theirs
                secureStorageKeyRepository.getL1Key() ?: throw e
            }
        }
    }

    override suspend fun getl2Key(): Key {
        l2KeyMutex.withLock {
            val userPassword = secureStorageKeyRepository.getPassword() ?: run {
                val newPassword = randomBytesGenerator.generateBytes(PASSWORD_SIZE)
                try {
                    secureStorageKeyRepository.setPassword(newPassword)
                    newPassword
                } catch (e: SecureStorageException.KeyAlreadyExistsException) {
                    // Another process wrote the password between our read and write — use theirs
                    secureStorageKeyRepository.getPassword() ?: throw e
                }
            }

            return getl2Key(userPassword!!.toByteString().base64())
        }
    }

    private suspend fun getl2Key(password: String): Key {
        val (encryptedL2Key, encryptedL2KeyIV) = secureStorageKeyRepository.getEncryptedL2Key() to secureStorageKeyRepository.getEncryptedL2KeyIV()
        val keyMaterial = if (encryptedL2Key != null && encryptedL2KeyIV != null) {
            encryptionHelper.decrypt(
                EncryptedBytes(
                    encryptedL2Key,
                    encryptedL2KeyIV,
                ),
                deriveKeyFromPassword(password),
            )
        } else {
            val keyBytes = secureStorageKeyGenerator.generateKey().encoded
            try {
                encryptAndStoreL2Key(keyBytes, password)
                keyBytes
            } catch (e: SecureStorageException.KeyAlreadyExistsException) {
                // Another process wrote the L2 key between our read and write — decrypt and use theirs
                encryptionHelper.decrypt(
                    EncryptedBytes(
                        secureStorageKeyRepository.getEncryptedL2Key() ?: throw e,
                        secureStorageKeyRepository.getEncryptedL2KeyIV() ?: throw e,
                    ),
                    deriveKeyFromPassword(password),
                )
            }
        }
        return secureStorageKeyGenerator.generateKeyFromKeyMaterial(keyMaterial)
    }

    private suspend fun encryptAndStoreL2Key(
        keyBytes: ByteArray,
        password: String,
    ): ByteArray =
        encryptionHelper.encrypt(
            keyBytes,
            deriveKeyFromPassword(password),
        ).also {
            secureStorageKeyRepository.setEncryptedL2Key(it)
        }.data

    private suspend fun getPasswordSalt(): ByteArray {
        secureStorageKeyRepository.getPasswordSalt()?.let {
            return it
        }
        val newSalt = randomBytesGenerator.generateBytes(PASSWORD_KEY_SALT_SIZE)
        return try {
            secureStorageKeyRepository.setPasswordSalt(newSalt)
            newSalt
        } catch (e: SecureStorageException.KeyAlreadyExistsException) {
            // Another process wrote the salt between our read and write — use theirs
            secureStorageKeyRepository.getPasswordSalt() ?: throw e
        }
    }

    private suspend fun deriveKeyFromPassword(password: String) =
        secureStorageKeyGenerator.generateKeyFromPassword(password, getPasswordSalt())

    companion object {
        private const val L1_PASSPHRASE_SIZE = 32
        private const val PASSWORD_SIZE = 32
        private const val PASSWORD_KEY_SALT_SIZE = 32
    }
}
