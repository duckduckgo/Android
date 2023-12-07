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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.impl.encryption.RandomBytesGenerator
import com.duckduckgo.securestorage.store.SecureStorageKeyRepository
import com.squareup.anvil.annotations.ContributesBinding
import java.security.Key
import javax.inject.Inject
import okio.ByteString.Companion.toByteString

/**
 * This class provides the usable decrypted keys to be used in various levels on encryption
 */
interface SecureStorageKeyProvider {
    fun canAccessKeyStore(): Boolean

    /**
     * Ready to use key for L1 encryption
     */
    fun getl1Key(): ByteArray

    /**
     * Ready to use key for L2 encryption using the generated user password
     */
    fun getl2Key(): Key
}

@ContributesBinding(AppScope::class)
class RealSecureStorageKeyProvider @Inject constructor(
    private val randomBytesGenerator: RandomBytesGenerator,
    private val secureStorageKeyRepository: SecureStorageKeyRepository,
    private val encryptionHelper: EncryptionHelper,
    private val secureStorageKeyGenerator: SecureStorageKeyGenerator,
) : SecureStorageKeyProvider {

    override fun canAccessKeyStore(): Boolean = secureStorageKeyRepository.canUseEncryption()

    @Synchronized
    override fun getl1Key(): ByteArray {
        // If no key exists in the keystore, we generate a new one and store it
        return if (secureStorageKeyRepository.l1Key == null) {
            randomBytesGenerator.generateBytes(L1_PASSPHRASE_SIZE).also {
                secureStorageKeyRepository.l1Key = it
            }
        } else {
            secureStorageKeyRepository.l1Key!!
        }
    }

    @Synchronized
    override fun getl2Key(): Key {
        val userPassword = if (secureStorageKeyRepository.password == null) {
            randomBytesGenerator.generateBytes(PASSWORD_SIZE).also {
                secureStorageKeyRepository.password = it
            }
        } else {
            secureStorageKeyRepository.password
        }

        return getl2Key(userPassword!!.toByteString().base64())
    }

    private fun getl2Key(password: String): Key {
        val keyMaterial = if (secureStorageKeyRepository.encryptedL2Key == null) {
            secureStorageKeyGenerator.generateKey().encoded.also {
                encryptAndStoreL2Key(it, password)
            }
        } else {
            encryptionHelper.decrypt(
                EncryptedBytes(
                    secureStorageKeyRepository.encryptedL2Key!!,
                    secureStorageKeyRepository.encryptedL2KeyIV!!,
                ),
                deriveKeyFromPassword(password),
            )
        }
        return secureStorageKeyGenerator.generateKeyFromKeyMaterial(keyMaterial)
    }

    private fun encryptAndStoreL2Key(
        keyBytes: ByteArray,
        password: String,
    ): ByteArray =
        encryptionHelper.encrypt(
            keyBytes,
            deriveKeyFromPassword(password),
        ).also {
            secureStorageKeyRepository.encryptedL2Key = it.data
            secureStorageKeyRepository.encryptedL2KeyIV = it.iv
        }.data

    private fun getPasswordSalt() = if (secureStorageKeyRepository.passwordSalt == null) {
        randomBytesGenerator.generateBytes(PASSWORD_KEY_SALT_SIZE).also {
            secureStorageKeyRepository.passwordSalt = it
        }
    } else {
        secureStorageKeyRepository.passwordSalt!!
    }

    private fun deriveKeyFromPassword(password: String) =
        secureStorageKeyGenerator.generateKeyFromPassword(password, getPasswordSalt())

    companion object {
        private const val L1_PASSPHRASE_SIZE = 32
        private const val PASSWORD_SIZE = 32
        private const val PASSWORD_KEY_SALT_SIZE = 32
    }
}
