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
import com.duckduckgo.securestorage.impl.encryption.EncryptionHelper.EncryptedBytes
import com.duckduckgo.securestorage.impl.encryption.EncryptionHelper.EncryptedString
import com.squareup.anvil.annotations.ContributesBinding
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

interface EncryptionHelper {
    fun encrypt(raw: ByteArray, key: Key): EncryptedBytes

    fun decrypt(toDecrypt: EncryptedBytes, key: Key): ByteArray

    fun encrypt(raw: String, key: Key): EncryptedString

    fun decrypt(toDecrypt: EncryptedString, key: Key): String

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
    private val cipher = Cipher.getInstance(TRANSFORMATION)

    override fun encrypt(raw: ByteArray, key: Key): EncryptedBytes {
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(raw)
        val iv = cipher.iv

        return EncryptedBytes(encrypted, iv)
    }

    override fun decrypt(toDecrypt: EncryptedBytes, key: Key): ByteArray {
        val ivSpec = GCMParameterSpec(GCM_PARAM_SPEC_LENGTH, toDecrypt.iv)
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)

        return cipher.doFinal(toDecrypt.data)
    }

    override fun encrypt(raw: String, key: Key): EncryptedString {
        // get ByteArray -> encrypt -> encode to String
        return encrypt(raw.toByteArray(), key).run {
            EncryptedString(
                this.data.transformToString(),
                this.iv.transformToString()
            )
        }
    }

    override fun decrypt(toDecrypt: EncryptedString, key: Key): String {
        // decode to ByteArray -> decrypt -> get String
        val encryptedBytes = EncryptedBytes(
            toDecrypt.data.transformToByteArray(),
            toDecrypt.iv.transformToByteArray()
        )
        return String(decrypt(encryptedBytes, key))
    }

    private fun String.transformToByteArray(): ByteArray = this.decodeBase64()?.toByteArray() ?: ByteArray(0)

    private fun ByteArray.transformToString(): String = this.toByteString().base64()

    companion object {
        private const val GCM_PARAM_SPEC_LENGTH = 128
        private const val TRANSFORMATION =
            "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
    }
}
