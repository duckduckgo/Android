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

package com.duckduckgo.securestorage.store.keys

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.lang.Exception
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/**
 * This class provides a way to access and store key related data
 */
interface SecureStorageKeyStore {

    fun updateKey(
        keyName: String,
        keyValue: ByteArray?,
    )

    fun getKey(keyName: String): ByteArray?

    /**
     * This method can be used to check if the keystore implementation has for support for encryption
     *
     * @return `true` if all the crypto dependencies needed by keystore is available and `false` otherwise
     */
    fun canUseEncryption(): Boolean
}

class RealSecureStorageKeyStore constructor(
    private val context: Context,
) : SecureStorageKeyStore {

    private val encryptedPreferences: SharedPreferences? by lazy { encryptedPreferences() }

    @Synchronized
    private fun encryptedPreferences(): SharedPreferences? {
        return try {
            EncryptedSharedPreferences.create(
                context,
                FILENAME,
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun updateKey(
        keyName: String,
        keyValue: ByteArray?,
    ) {
        encryptedPreferences?.edit(commit = true) {
            if (keyValue == null) {
                remove(keyName)
            } else {
                putString(keyName, keyValue.toByteString().base64())
            }
        }
    }

    override fun getKey(keyName: String): ByteArray? = encryptedPreferences?.getString(keyName, null)?.run {
        this.decodeBase64()?.toByteArray()
    }

    override fun canUseEncryption(): Boolean = encryptedPreferences != null

    companion object {
        const val FILENAME = "com.duckduckgo.securestorage.store"
    }
}
