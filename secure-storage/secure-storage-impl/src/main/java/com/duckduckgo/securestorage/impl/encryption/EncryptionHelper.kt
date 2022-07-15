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

package com.duckduckgo.securestorage.impl.encryption

import android.security.keystore.KeyProperties
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.api.SecureStorageException
import com.duckduckgo.securestorage.api.SecureStorageException.InternalSecureStorageException
import com.duckduckgo.securestorage.impl.encryption.EncryptionHelper.EncryptedBytes
import com.squareup.anvil.annotations.ContributesBinding
import java.lang.Exception
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

interface EncryptionHelper {
    @Throws(SecureStorageException::class)
    fun encrypt(
        raw: ByteArray,
        key: Key
    ): EncryptedBytes

    @Throws(SecureStorageException::class)
    fun decrypt(
        toDecrypt: EncryptedBytes,
        key: Key
    ): ByteArray

    class EncryptedBytes(
        val data: ByteArray,
        val iv: ByteArray
    )

    class EncryptedString(
        val data: String,
        val iv: String
    )
}

@ContributesBinding(AppScope::class)
class RealEncryptionHelper @Inject constructor() : EncryptionHelper {
    private val encryptionCipher = Cipher.getInstance(TRANSFORMATION)
    private val decryptionCipher = Cipher.getInstance(TRANSFORMATION)

    @Synchronized
    override fun encrypt(
        raw: ByteArray,
        key: Key
    ): EncryptedBytes {
        val encrypted = try {
            encryptionCipher.init(Cipher.ENCRYPT_MODE, key)
            encryptionCipher.doFinal(raw)
        } catch (exception: Exception) {
            throw InternalSecureStorageException(message = "Error occurred while encrypting data", cause = exception)
        }
        val iv = encryptionCipher.iv

        return EncryptedBytes(encrypted, iv)
    }

    @Synchronized
    override fun decrypt(
        toDecrypt: EncryptedBytes,
        key: Key
    ): ByteArray {
        return try {
            val ivSpec = GCMParameterSpec(GCM_PARAM_SPEC_LENGTH, toDecrypt.iv)
            decryptionCipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
            decryptionCipher.doFinal(toDecrypt.data)
        } catch (exception: Exception) {
            throw InternalSecureStorageException(message = "Error occurred while decrypting data", cause = exception)
        }
    }

    companion object {
        private const val GCM_PARAM_SPEC_LENGTH = 128
        private const val TRANSFORMATION =
            "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
    }
}
