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

package com.duckduckgo.pir.impl.store.secure

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import javax.inject.Inject

/**
 * This class provides a way to access and store key related data
 */
interface PirSecureStorageKeyStore {

    suspend fun updateKey(
        keyName: String,
        keyValue: ByteArray?,
    )

    suspend fun getKey(keyName: String): ByteArray?

    /**
     * This method can be used to check if the keystore implementation has for support for encryption
     *
     * @return `true` if all the crypto dependencies needed by keystore are available and `false` otherwise
     */
    suspend fun canUseEncryption(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = PirSecureStorageKeyStore::class,
)
class RealPirSecureStorageKeyStore @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : PirSecureStorageKeyStore {

    private val mutex: Mutex = Mutex()
    private val encryptedPreferencesDeferred: Deferred<SharedPreferences?> by lazy {
        coroutineScope.async(dispatcherProvider.io()) {
            createEncryptedPreferences()
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

    private suspend fun getEncryptedPreferences(): SharedPreferences? {
        return encryptedPreferencesDeferred.await()
    }

    private suspend fun createEncryptedPreferences(): SharedPreferences? {
        return try {
            mutex.withLock {
                sharedPreferencesProvider.getEncryptedSharedPreferences(
                    name = FILENAME,
                    multiprocess = true,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.pir.impl.store.secure"
    }
}
