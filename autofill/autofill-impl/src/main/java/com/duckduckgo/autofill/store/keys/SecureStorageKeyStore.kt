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

package com.duckduckgo.autofill.store.keys

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.common.utils.DispatcherProvider
import java.lang.Exception
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/**
 * This class provides a way to access and store key related data
 */
interface SecureStorageKeyStore {

    suspend fun updateKey(
        keyName: String,
        keyValue: ByteArray?,
    )

    suspend fun getKey(keyName: String): ByteArray?

    /**
     * This method can be used to check if the keystore implementation has for support for encryption
     *
     * @return `true` if all the crypto dependencies needed by keystore is available and `false` otherwise
     */
    suspend fun canUseEncryption(): Boolean
}

class RealSecureStorageKeyStore constructor(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val autofillFeature: AutofillFeature,
) : SecureStorageKeyStore {

    private val mutex: Mutex = Mutex()
    private val encryptedPreferencesDeferred: Deferred<SharedPreferences?> by lazy {
        coroutineScope.async(dispatcherProvider.io()) {
            encryptedPreferencesAsync()
        }
    }

    private val encryptedPreferencesSync: SharedPreferences? by lazy { encryptedPreferencesSync() }

    private suspend fun getEncryptedPreferences(): SharedPreferences? {
        return if (autofillFeature.createAsyncPreferences().isEnabled()) encryptedPreferencesDeferred.await() else encryptedPreferencesSync
    }

    private suspend fun encryptedPreferencesAsync(): SharedPreferences? {
        return try {
            mutex.withLock {
                EncryptedSharedPreferences.create(
                    context,
                    FILENAME,
                    MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    private fun encryptedPreferencesSync(): SharedPreferences? {
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

    override suspend fun updateKey(
        keyName: String,
        keyValue: ByteArray?,
    ) {
        withContext(dispatcherProvider.io()) {
            getEncryptedPreferences()?.edit(commit = true) {
                if (keyValue == null) {
                    remove(keyName)
                } else {
                    putString(keyName, keyValue.toByteString().base64())
                }
            }
        }
    }

    override suspend fun getKey(keyName: String): ByteArray? {
        return withContext(dispatcherProvider.io()) {
            return@withContext getEncryptedPreferences()?.getString(keyName, null)?.run {
                this.decodeBase64()?.toByteArray()
            }
        }
    }

    override suspend fun canUseEncryption(): Boolean = withContext(dispatcherProvider.io()) {
        getEncryptedPreferences() != null
    }

    companion object {
        const val FILENAME = "com.duckduckgo.securestorage.store"
    }
}
