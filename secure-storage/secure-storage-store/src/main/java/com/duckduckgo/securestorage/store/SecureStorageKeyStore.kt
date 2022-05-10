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

package com.duckduckgo.securestorage.store

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.lang.Exception

/**
 * This class provides a way to access and store key related data
 */
interface SecureStorageKeyStore {
    /**
     * User / Programmatically generated password to be used for L2 encryption
     */
    var password: ByteArray?

    /**
     * Key used for L1 encryption
     */
    var l1Key: ByteArray?

    /**
     * Encrypted key that can be decrypted to be used for L2 encryption
     */
    var encryptedL2Key: ByteArray?

    /**
     * This method can be check if the keystore has support encryption
     *
     * @return `true` if all the crypto dependencies needed by description is available and `false` otherwise
     */
    fun canUseEncryption(): Boolean
}

class RealSecureStorageKeyStore constructor(
    private val context: Context
) : SecureStorageKeyStore {
    companion object {
        const val FILENAME = "com.duckduckgo.securestorage.store"
        const val KEY_GENERATED_PASSWORD = "KEY_GENERATED_PASSWORD"
        const val KEY_L1KEY = "KEY_L1KEY"
        const val KEY_ENCRYPTED_L2KEY = "KEY_ENCRYPTED_L2KEY"
    }

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
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            null
        }
    }

    override var password: ByteArray?
        get() = getValue(KEY_GENERATED_PASSWORD)
        set(value) {
            updateValue(KEY_GENERATED_PASSWORD, value)
        }

    override var l1Key: ByteArray?
        get() = getValue(KEY_L1KEY)
        set(value) {
            updateValue(KEY_L1KEY, value)
        }

    override var encryptedL2Key: ByteArray?
        get() = getValue(KEY_ENCRYPTED_L2KEY)
        set(value) {
            updateValue(KEY_ENCRYPTED_L2KEY, value)
        }

    private fun updateValue(
        key: String,
        value: ByteArray?
    ) {
        Base64.encodeToString(value, Base64.DEFAULT).also {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) remove(key)
                else putString(key, it)
            }
        }
    }

    private fun getValue(key: String): ByteArray? {
        return encryptedPreferences?.getString(key, null)?.run {
            Base64.decode(this, Base64.DEFAULT)
        }
    }

    override fun canUseEncryption(): Boolean = encryptedPreferences != null
}
