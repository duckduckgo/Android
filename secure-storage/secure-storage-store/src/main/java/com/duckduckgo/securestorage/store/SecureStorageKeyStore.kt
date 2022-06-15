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
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
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
     * Salt to be used when generating the key for l2 encryption from the password
     */
    var passwordSalt: ByteArray?

    /**
     * Encrypted key that can be decrypted to be used for L2 encryption
     */
    var encryptedL2Key: ByteArray?

    /**
     * Iv to be used for L2 key decryption
     */
    var encryptedL2KeyIV: ByteArray?

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

    override var passwordSalt: ByteArray?
        get() = getValue(KEY_PASSWORD_SALT)
        set(value) {
            updateValue(KEY_PASSWORD_SALT, value)
        }

    override var encryptedL2Key: ByteArray?
        get() = getValue(KEY_ENCRYPTED_L2KEY)
        set(value) {
            updateValue(KEY_ENCRYPTED_L2KEY, value)
        }

    override var encryptedL2KeyIV: ByteArray?
        get() = getValue(KEY_ENCRYPTED_L2KEY_IV)
        set(value) {
            updateValue(KEY_ENCRYPTED_L2KEY_IV, value)
        }

    private fun updateValue(
        key: String,
        value: ByteArray?
    ) {
        encryptedPreferences?.edit(commit = true) {
            if (value == null) remove(key)
            else putString(key, value.toByteString().base64())
        }
    }

    private fun getValue(key: String): ByteArray? {
        return encryptedPreferences?.getString(key, null)?.run {
            this.decodeBase64()?.toByteArray()
        }
    }

    override fun canUseEncryption(): Boolean = encryptedPreferences != null

    companion object {
        const val FILENAME = "com.duckduckgo.securestorage.store"
        const val KEY_GENERATED_PASSWORD = "KEY_GENERATED_PASSWORD"
        const val KEY_L1KEY = "KEY_L1KEY"
        const val KEY_PASSWORD_SALT = "KEY_PASSWORD_SALT"
        const val KEY_ENCRYPTED_L2KEY = "KEY_ENCRYPTED_L2KEY"
        const val KEY_ENCRYPTED_L2KEY_IV = "KEY_ENCRYPTED_L2KEY_IV"
    }
}
