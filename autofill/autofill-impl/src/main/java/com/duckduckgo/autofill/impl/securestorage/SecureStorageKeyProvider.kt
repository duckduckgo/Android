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

import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.securestorage.encryption.EncryptionHelper
import com.duckduckgo.autofill.impl.securestorage.encryption.EncryptionHelper.EncryptedBytes
import com.duckduckgo.autofill.store.SecureStorageKeyRepository
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.impl.encryption.RandomBytesGenerator
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.runBlocking
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
    private val autofillFeature: AutofillFeature,
) : SecureStorageKeyProvider {

    override suspend fun canAccessKeyStore(): Boolean = secureStorageKeyRepository.canUseEncryption()
    private val l1KeyMutex = Mutex()
    private val l2KeyMutex = Mutex()

    override suspend fun getl1Key(): ByteArray {
        if (autofillFeature.createAsyncPreferences().isEnabled()) {
            return getl1KeyAsync()
        } else {
            return getl1KeySync()
        }
    }

    private suspend fun getl1KeyAsync(): ByteArray {
        l1KeyMutex.withLock {
            return innerGetL1Key()
        }
    }

    @Synchronized
    private fun getl1KeySync(): ByteArray {
        return runBlocking {
            innerGetL1Key()
        }
    }

    private suspend fun innerGetL1Key(): ByteArray {
        // If no key exists in the keystore, we generate a new one and store it
        return if (secureStorageKeyRepository.getL1Key() == null) {
            randomBytesGenerator.generateBytes(L1_PASSPHRASE_SIZE).also {
                secureStorageKeyRepository.setL1Key(it)
            }
        } else {
            secureStorageKeyRepository.getL1Key()!!
        }
    }

    override suspend fun getl2Key(): Key {
        if (autofillFeature.createAsyncPreferences().isEnabled()) {
            return getl2KeyAsync()
        } else {
            return getl2KeySync()
        }
    }

    private suspend fun getl2KeyAsync(): Key {
        return l2KeyMutex.withLock {
            innerGetL2Key()
        }
    }

    @Synchronized
    private fun getl2KeySync(): Key {
        return runBlocking {
            innerGetL2Key()
        }
    }

    private suspend fun innerGetL2Key(): Key {
        val userPassword = if (secureStorageKeyRepository.getPassword() == null) {
            randomBytesGenerator.generateBytes(PASSWORD_SIZE).also {
                secureStorageKeyRepository.setPassword(it)
            }
        } else {
            secureStorageKeyRepository.getPassword()
        }

        return getl2Key(userPassword!!.toByteString().base64())
    }

    private suspend fun getl2Key(password: String): Key {
        val keyMaterial = if (secureStorageKeyRepository.getEncryptedL2Key() == null) {
            secureStorageKeyGenerator.generateKey().encoded.also {
                encryptAndStoreL2Key(it, password)
            }
        } else {
            encryptionHelper.decrypt(
                EncryptedBytes(
                    secureStorageKeyRepository.getEncryptedL2Key()!!,
                    secureStorageKeyRepository.getEncryptedL2KeyIV()!!,
                ),
                deriveKeyFromPassword(password),
            )
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
            secureStorageKeyRepository.setEncryptedL2Key(it.data)
            secureStorageKeyRepository.setEncryptedL2KeyIV(it.iv)
        }.data

    private suspend fun getPasswordSalt() = if (secureStorageKeyRepository.getPasswordSalt() == null) {
        randomBytesGenerator.generateBytes(PASSWORD_KEY_SALT_SIZE).also {
            secureStorageKeyRepository.setPasswordSalt(it)
        }
    } else {
        secureStorageKeyRepository.getPasswordSalt()!!
    }

    private suspend fun deriveKeyFromPassword(password: String) =
        secureStorageKeyGenerator.generateKeyFromPassword(password, getPasswordSalt())

    companion object {
        private const val L1_PASSPHRASE_SIZE = 32
        private const val PASSWORD_SIZE = 32
        private const val PASSWORD_KEY_SALT_SIZE = 32
    }
}
