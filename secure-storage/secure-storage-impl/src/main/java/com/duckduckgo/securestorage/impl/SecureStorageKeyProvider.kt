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

package com.duckduckgo.securestorage.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.impl.encryption.EncryptionHelper
import com.duckduckgo.securestorage.impl.encryption.EncryptionHelper.EncryptedBytes
import com.duckduckgo.securestorage.impl.encryption.RandomBytesGenerator
import com.duckduckgo.securestorage.store.SecureStorageKeyStore
import com.squareup.anvil.annotations.ContributesBinding
import okio.ByteString.Companion.toByteString
import java.security.Key
import javax.inject.Inject

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
    private val secureStorageKeyStore: SecureStorageKeyStore,
    private val encryptionHelper: EncryptionHelper,
    private val secureStorageKeyGenerator: SecureStorageKeyGenerator
) : SecureStorageKeyProvider {

    override fun canAccessKeyStore(): Boolean = secureStorageKeyStore.canUseEncryption()

    @Synchronized
    override fun getl1Key(): ByteArray {
        // If no key exists in the keystore, we generate a new one and store it
        return if (secureStorageKeyStore.l1Key == null) {
            randomBytesGenerator.generateBytes(L1_PASSPHRASE_SIZE).also {
                secureStorageKeyStore.l1Key = it
            }
        } else {
            secureStorageKeyStore.l1Key!!
        }
    }

    @Synchronized
    override fun getl2Key(): Key {
        val userPassword = if (secureStorageKeyStore.password == null) {
            randomBytesGenerator.generateBytes(PASSWORD_SIZE).also {
                secureStorageKeyStore.password = it
            }
        } else {
            secureStorageKeyStore.password
        }

        return getl2Key(userPassword!!.toByteString().base64())
    }

    private fun getl2Key(password: String): Key {
        val keyMaterial = if (secureStorageKeyStore.encryptedL2Key == null) {
            secureStorageKeyGenerator.generateKey().encoded.also {
                encryptAndStoreL2Key(it, password)
            }
        } else {
            encryptionHelper.decrypt(
                EncryptedBytes(
                    secureStorageKeyStore.encryptedL2Key!!,
                    secureStorageKeyStore.encryptedL2KeyIV!!
                ),
                deriveKeyFromPassword(password)
            )
        }
        return secureStorageKeyGenerator.generateKeyFromKeyMaterial(keyMaterial)
    }

    private fun encryptAndStoreL2Key(
        keyBytes: ByteArray,
        password: String
    ): ByteArray =
        encryptionHelper.encrypt(
            keyBytes,
            deriveKeyFromPassword(password)
        ).also {
            secureStorageKeyStore.encryptedL2Key = it.data
            secureStorageKeyStore.encryptedL2KeyIV = it.iv
        }.data

    private fun getPasswordSalt() = if (secureStorageKeyStore.passwordSalt == null) {
        randomBytesGenerator.generateBytes(PASSWORD_KEY_SALT_SIZE).also {
            secureStorageKeyStore.passwordSalt = it
        }
    } else {
        secureStorageKeyStore.passwordSalt!!
    }

    private fun deriveKeyFromPassword(password: String) =
        secureStorageKeyGenerator.generateKeyFromPassword(password, getPasswordSalt())

    companion object {
        private const val L1_PASSPHRASE_SIZE = 32
        private const val PASSWORD_SIZE = 32
        private const val PASSWORD_KEY_SALT_SIZE = 32
    }
}
