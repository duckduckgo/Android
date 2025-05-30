/*
 * Copyright (c) 2025 DuckDuckGo
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

import com.duckduckgo.autofill.store.keys.SecureStorageKeyStore

interface SecureStorageKeyRepository {
    /**
     * User / Programmatically generated password to be used for L2 encryption
     */
    suspend fun getPassword(): ByteArray?
    suspend fun setPassword(value: ByteArray?)

    /**
     * Key used for L1 encryption
     */
    suspend fun getL1Key(): ByteArray?
    suspend fun setL1Key(value: ByteArray?)

    /**
     * Salt to be used when generating the key for l2 encryption from the password
     */
    suspend fun getPasswordSalt(): ByteArray?
    suspend fun setPasswordSalt(value: ByteArray?)

    /**
     * Encrypted key that can be decrypted to be used for L2 encryption
     */
    suspend fun getEncryptedL2Key(): ByteArray?
    suspend fun setEncryptedL2Key(value: ByteArray?)

    /**
     * Iv to be used for L2 key decryption
     */
    suspend fun getEncryptedL2KeyIV(): ByteArray?
    suspend fun setEncryptedL2KeyIV(value: ByteArray?)

    /**
     * This method can be checked if the keystore has support for encryption
     *
     * @return `true` if keystore encryption is supported and `false` otherwise
     */
    suspend fun canUseEncryption(): Boolean
}

class RealSecureStorageKeyRepository constructor(
    private val keyStore: SecureStorageKeyStore,
) : SecureStorageKeyRepository {
    override suspend fun getPassword(): ByteArray? = keyStore.getKey(KEY_GENERATED_PASSWORD)
    override suspend fun setPassword(value: ByteArray?) {
        keyStore.updateKey(KEY_GENERATED_PASSWORD, value)
    }

    override suspend fun getL1Key(): ByteArray? = keyStore.getKey(KEY_L1KEY)
    override suspend fun setL1Key(value: ByteArray?) {
        keyStore.updateKey(KEY_L1KEY, value)
    }

    override suspend fun getPasswordSalt(): ByteArray? = keyStore.getKey(KEY_PASSWORD_SALT)
    override suspend fun setPasswordSalt(value: ByteArray?) {
        keyStore.updateKey(KEY_PASSWORD_SALT, value)
    }

    override suspend fun getEncryptedL2Key(): ByteArray? = keyStore.getKey(KEY_ENCRYPTED_L2KEY)
    override suspend fun setEncryptedL2Key(value: ByteArray?) {
        keyStore.updateKey(KEY_ENCRYPTED_L2KEY, value)
    }

    override suspend fun getEncryptedL2KeyIV(): ByteArray? = keyStore.getKey(KEY_ENCRYPTED_L2KEY_IV)
    override suspend fun setEncryptedL2KeyIV(value: ByteArray?) {
        keyStore.updateKey(KEY_ENCRYPTED_L2KEY_IV, value)
    }

    override suspend fun canUseEncryption(): Boolean = keyStore.canUseEncryption()

    companion object {
        const val KEY_GENERATED_PASSWORD = "KEY_GENERATED_PASSWORD"
        const val KEY_L1KEY = "KEY_L1KEY"
        const val KEY_PASSWORD_SALT = "KEY_PASSWORD_SALT"
        const val KEY_ENCRYPTED_L2KEY = "KEY_ENCRYPTED_L2KEY"
        const val KEY_ENCRYPTED_L2KEY_IV = "KEY_ENCRYPTED_L2KEY_IV"
    }
}
