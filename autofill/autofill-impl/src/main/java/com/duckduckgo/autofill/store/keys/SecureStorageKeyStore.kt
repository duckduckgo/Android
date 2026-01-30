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
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_GET_KEY_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_RETRIEVAL_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_UPDATE_KEY_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PREFERENCES_GET_KEY_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PREFERENCES_UPDATE_KEY_FAILED
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.sanitizeStackTrace
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.logcat
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
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val pixel: Pixel,
) : SecureStorageKeyStore {

    private val mutex: Mutex = Mutex()
    private val harmonyMutex: Mutex = Mutex()
    private val encryptedPreferencesDeferred: Deferred<SharedPreferences?> by lazy {
        coroutineScope.async(dispatcherProvider.io()) {
            try {
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
                coroutineContext.ensureActive()
                pixel.fire(
                    AutofillPixelNames.AUTOFILL_PREFERENCES_RETRIEVAL_FAILED,
                    mapOf("error" to e.error()),
                    type = Daily(),
                )
                null
            }
        }
    }

    private val harmonyPreferencesDeferred: Deferred<SharedPreferences?> by lazy {
        coroutineScope.async(dispatcherProvider.io()) {
            try {
                harmonyMutex.withLock {
                    if (autofillFeature.useHarmony().isEnabled()) {
                        sharedPreferencesProvider.getMigratedEncryptedSharedPreferences(FILENAME).also {
                            if (it == null) {
                                logcat { "autofill harmony preferences retrieval returned null" }
                            }
                        }
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                pixel.fire(
                    AUTOFILL_HARMONY_PREFERENCES_RETRIEVAL_FAILED,
                    mapOf("error" to e.error()),
                    type = Daily(),
                )
                logcat { "autofill harmony preferences retrieval failed: $e" }
                null
            }
        }
    }

    private suspend fun getEncryptedPreferences(): SharedPreferences? {
        return encryptedPreferencesDeferred.await()
    }

    private suspend fun getHarmonyEncryptedPreferences(): SharedPreferences? {
        return harmonyPreferencesDeferred.await()
    }

    override suspend fun updateKey(
        keyName: String,
        keyValue: ByteArray?,
    ) {
        withContext(dispatcherProvider.io()) {
            runCatching {
                getEncryptedPreferences()?.edit(commit = true) {
                    if (keyValue == null) {
                        remove(keyName)
                    } else {
                        putString(keyName, keyValue.toByteString().base64())
                    }
                }
            }.getOrElse {
                ensureActive()
                pixel.fire(
                    AUTOFILL_PREFERENCES_UPDATE_KEY_FAILED,
                    mapOf("error" to it.error()),
                    type = Daily(),
                )
                throw it
            }

            if (autofillFeature.useHarmony().isEnabled()) {
                runCatching {
                    getHarmonyEncryptedPreferences()?.edit(commit = true) {
                        if (keyValue == null) {
                            remove(keyName)
                        } else {
                            putString(keyName, keyValue.toByteString().base64())
                        }
                    }
                }.getOrElse {
                    ensureActive()
                    pixel.fire(
                        AUTOFILL_HARMONY_PREFERENCES_UPDATE_KEY_FAILED,
                        mapOf("error" to it.error()),
                        type = Daily(),
                    )
                    throw it
                }
            }
        }
    }

    override suspend fun getKey(keyName: String): ByteArray? {
        return withContext(dispatcherProvider.io()) {
            val useHarmony = autofillFeature.useHarmony().isEnabled()
            val preferences = if (useHarmony) {
                getHarmonyEncryptedPreferences()
            } else {
                getEncryptedPreferences()
            }
            return@withContext runCatching {
                preferences?.getString(keyName, null)?.run {
                    this.decodeBase64()?.toByteArray()
                }
            }.getOrElse {
                ensureActive()
                val pixelName = if (useHarmony) {
                    AUTOFILL_HARMONY_PREFERENCES_GET_KEY_FAILED
                } else {
                    AUTOFILL_PREFERENCES_GET_KEY_FAILED
                }
                pixel.fire(
                    pixelName,
                    mapOf("error" to it.error()),
                    type = Daily(),
                )
                throw it
            }
        }
    }

    override suspend fun canUseEncryption(): Boolean = withContext(dispatcherProvider.io()) {
        if (autofillFeature.useHarmony().isEnabled()) {
            getHarmonyEncryptedPreferences() != null
        } else {
            getEncryptedPreferences() != null
        }
    }

    private fun Throwable.error(): String {
        return if (autofillFeature.sendSanitizedStackTraces().isEnabled()) {
            sanitizeStackTrace()
        } else {
            javaClass.name
        }
    }

    companion object {
        const val FILENAME = "com.duckduckgo.securestorage.store"
    }
}
