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

package com.duckduckgo.autofill.impl.securestorage.encryption

import android.security.keystore.KeyProperties
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.securestorage.SecureStorageException
import com.duckduckgo.autofill.impl.securestorage.SecureStorageException.InternalSecureStorageException
import com.duckduckgo.autofill.impl.securestorage.encryption.EncryptionHelper.EncryptedBytes
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.lang.Exception
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface EncryptionHelper {
    @Throws(SecureStorageException::class)
    suspend fun encrypt(
        raw: ByteArray,
        key: Key,
    ): EncryptedBytes

    @Throws(SecureStorageException::class)
    suspend fun decrypt(
        toDecrypt: EncryptedBytes,
        key: Key,
    ): ByteArray

    class EncryptedBytes(
        val data: ByteArray,
        val iv: ByteArray,
    )

    class EncryptedString(
        val data: String,
        val iv: String,
    )
}

@ContributesBinding(AppScope::class)
class RealEncryptionHelper @Inject constructor(
    private val autofillFeature: AutofillFeature,
    private val dispatcherProvider: DispatcherProvider,
) : EncryptionHelper {
    private val encryptionCipher = Cipher.getInstance(TRANSFORMATION)
    private val decryptionCipher = Cipher.getInstance(TRANSFORMATION)

    private val encryptMutex = Mutex()
    private val decryptMutex = Mutex()

    override suspend fun encrypt(
        raw: ByteArray,
        key: Key,
    ): EncryptedBytes = withContext(dispatcherProvider.io()) {
        return@withContext if (autofillFeature.createAsyncPreferences().isEnabled()) {
            encryptAsync(raw, key)
        } else {
            encryptSync(raw, key)
        }
    }

    @Synchronized
    private fun encryptSync(
        raw: ByteArray,
        key: Key,
    ): EncryptedBytes {
        return innerEncrypt(raw, key)
    }

    private suspend fun encryptAsync(
        raw: ByteArray,
        key: Key,
    ): EncryptedBytes {
        encryptMutex.withLock {
            return innerEncrypt(raw, key)
        }
    }

    private fun innerEncrypt(
        raw: ByteArray,
        key: Key,
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

    override suspend fun decrypt(
        toDecrypt: EncryptedBytes,
        key: Key,
    ): ByteArray = withContext(dispatcherProvider.io()) {
        return@withContext if (autofillFeature.createAsyncPreferences().isEnabled()) {
            decryptAsync(toDecrypt, key)
        } else {
            decryptSync(toDecrypt, key)
        }
    }

    @Synchronized
    private fun decryptSync(
        toDecrypt: EncryptedBytes,
        key: Key,
    ): ByteArray {
        return innerDecrypt(toDecrypt, key)
    }

    private suspend fun decryptAsync(
        toDecrypt: EncryptedBytes,
        key: Key,
    ): ByteArray {
        decryptMutex.withLock {
            return innerDecrypt(toDecrypt, key)
        }
    }

    private fun innerDecrypt(
        toDecrypt: EncryptedBytes,
        key: Key,
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
