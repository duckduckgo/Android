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
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_HARMONY_KEY_MISMATCH
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_HARMONY_KEY_MISSING
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_GET_KEY_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_RETRIEVAL_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_UPDATE_KEY_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PREFERENCES_GET_KEY_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PREFERENCES_KEY_MISSING
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PREFERENCES_UPDATE_KEY_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_STORE_KEY_ALREADY_EXISTS
import com.duckduckgo.autofill.impl.securestorage.SecureStorageException
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.sanitizeStackTrace
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
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

    private var initialUseHarmonyValue: Boolean? = null

    private val useHarmonyDeferred: Deferred<Boolean> by lazy {
        coroutineScope.async(dispatcherProvider.io()) {
            autofillFeature.useHarmony().isEnabled()
        }
    }

    private val readFromHarmonyDeferred: Deferred<Boolean> by lazy {
        coroutineScope.async(dispatcherProvider.io()) {
            autofillFeature.readFromHarmony().isEnabled()
        }
    }

    private suspend fun useHarmony(): Boolean = useHarmonyDeferred.await()

    private suspend fun readFromHarmony(): Boolean = readFromHarmonyDeferred.await()
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
                    mapOf(
                        "error" to e.error(),
                        "addWriteGuard" to autofillFeature.addWriteGuard().isEnabled().toString(),
                        "addReadGuard" to autofillFeature.addReadGuard().isEnabled().toString(),
                    ),
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
                    useHarmony().let { useHarmony ->
                        initialUseHarmonyValue = useHarmony
                        if (useHarmony) {
                            getEncryptedPreferences()?.let { legacyPreferences ->
                                sharedPreferencesProvider.getMigratedEncryptedSharedPreferences(legacyPreferences, FILENAME_V2).also {
                                    if (it == null) {
                                        logcat { "autofill harmony preferences retrieval returned null" }
                                    }
                                }
                            } ?: run {
                                logcat { "autofill harmony preferences retrieval returned null" }
                                null
                            }
                        } else {
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                pixel.fire(
                    AUTOFILL_HARMONY_PREFERENCES_RETRIEVAL_FAILED,
                    mapOf(
                        "error" to e.error(),
                        "addWriteGuard" to autofillFeature.addWriteGuard().isEnabled().toString(),
                        "addReadGuard" to autofillFeature.addReadGuard().isEnabled().toString(),
                        "initialHarmonyValue" to initialUseHarmonyValue.toString(),
                    ),
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
            // Guard: encryption keys are write-once. If we're trying to write a non-null value
            // for a key that already exists in either store, something upstream read null
            // incorrectly and is about to overwrite a valid key — block the write to prevent
            // irreversible corruption.
            if (autofillFeature.addWriteGuard().isEnabled() && keyValue != null && keyAlreadyExists(keyName)) {
                pixel.fire(
                    AUTOFILL_STORE_KEY_ALREADY_EXISTS,
                    mapOf(
                        "key" to keyName,
                        "addWriteGuard" to autofillFeature.addWriteGuard().isEnabled().toString(),
                        "addReadGuard" to autofillFeature.addReadGuard().isEnabled().toString(),
                        "initialHarmonyValue" to initialUseHarmonyValue.toString(),
                        "readFromHarmony" to autofillFeature.readFromHarmony().isEnabled().toString(),
                    ),
                    type = Daily(),
                )
                throw SecureStorageException.InternalSecureStorageException("Trying to overwrite already existing key")
            }

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
                    mapOf(
                        "key" to keyName,
                        "error" to it.error(),
                        "addWriteGuard" to autofillFeature.addWriteGuard().isEnabled().toString(),
                        "addReadGuard" to autofillFeature.addReadGuard().isEnabled().toString(),
                    ),
                    type = Daily(),
                )
                throw it
            }

            if (useHarmony()) {
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
                        mapOf(
                            "key" to keyName,
                            "error" to it.error(),
                            "addWriteGuard" to autofillFeature.addWriteGuard().isEnabled().toString(),
                            "addReadGuard" to autofillFeature.addReadGuard().isEnabled().toString(),
                            "initialHarmonyValue" to initialUseHarmonyValue.toString(),
                            "readFromHarmony" to autofillFeature.readFromHarmony().isEnabled().toString(),
                        ),
                        type = Daily(),
                    )
                    throw it
                }
            }
        }
    }

    /**
     * Checks if the key already exists in either legacy or Harmony preferences.
     * Used as a write guard to prevent overwriting write-once encryption keys.
     */
    private suspend fun keyAlreadyExists(keyName: String): Boolean {
        val legacyExists = try {
            getEncryptedPreferences()?.getString(keyName, null) != null
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            false
        }
        if (legacyExists) return true

        if (useHarmony()) {
            val harmonyExists = try {
                getHarmonyEncryptedPreferences()?.getString(keyName, null) != null
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                false
            }
            if (harmonyExists) return true
        }

        return false
    }

    override suspend fun getKey(keyName: String): ByteArray? {
        return withContext(dispatcherProvider.io()) {
            // Always read from legacy — source of truth
            val legacyValue = getEncryptedPreferences().let {
                if (it == null && autofillFeature.addReadGuard().isEnabled()) {
                    throw SecureStorageException.InternalSecureStorageException("Preferences file is null on read")
                }
                runCatching {
                    it?.getString(keyName, null)?.run {
                        this.decodeBase64()?.toByteArray()
                    }
                }.getOrElse {
                    ensureActive()
                    pixel.fire(
                        AUTOFILL_PREFERENCES_GET_KEY_FAILED,
                        mapOf(
                            "key" to keyName,
                            "error" to it.error(),
                            "addWriteGuard" to autofillFeature.addWriteGuard().isEnabled().toString(),
                            "addReadGuard" to autofillFeature.addReadGuard().isEnabled().toString(),
                        ),
                        type = Daily(),
                    )
                    throw it
                }
            }

            // When useHarmony is ON, read Harmony and compare for diagnostic pixels
            if (useHarmony()) {
                val harmonyValue = getHarmonyEncryptedPreferences().let {
                    if (it == null && autofillFeature.addReadGuard().isEnabled()) {
                        throw SecureStorageException.InternalSecureStorageException("Preferences file is null on read")
                    }
                    runCatching {
                        it?.getString(keyName, null)?.run {
                            this.decodeBase64()?.toByteArray()
                        }
                    }.getOrElse {
                        ensureActive()
                        pixel.fire(
                            AUTOFILL_HARMONY_PREFERENCES_GET_KEY_FAILED,
                            mapOf(
                                "key" to keyName,
                                "error" to it.error(),
                                "addWriteGuard" to autofillFeature.addWriteGuard().isEnabled().toString(),
                                "addReadGuard" to autofillFeature.addReadGuard().isEnabled().toString(),
                                "initialHarmonyValue" to initialUseHarmonyValue.toString(),
                                "readFromHarmony" to autofillFeature.readFromHarmony().isEnabled().toString(),
                            ),
                            type = Daily(),
                        )
                        if (autofillFeature.addReadGuard().isEnabled()) {
                            throw SecureStorageException.InternalSecureStorageException("Harmony preferences getKey failed")
                        }
                        null
                    }
                }

                when {
                    harmonyValue == null && legacyValue != null -> {
                        pixel.fire(
                            AUTOFILL_HARMONY_KEY_MISSING,
                            mapOf(
                                "key" to keyName,
                                "addWriteGuard" to autofillFeature.addWriteGuard().isEnabled().toString(),
                                "addReadGuard" to autofillFeature.addReadGuard().isEnabled().toString(),
                                "initialHarmonyValue" to initialUseHarmonyValue.toString(),
                                "readFromHarmony" to autofillFeature.readFromHarmony().isEnabled().toString(),
                            ),
                            type = Daily(),
                        )
                        if (autofillFeature.addReadGuard().isEnabled()) {
                            throw SecureStorageException.InternalSecureStorageException("Harmony key missing")
                        }
                    }
                    harmonyValue != null && legacyValue == null -> {
                        pixel.fire(
                            AUTOFILL_PREFERENCES_KEY_MISSING,
                            mapOf(
                                "key" to keyName,
                                "addWriteGuard" to autofillFeature.addWriteGuard().isEnabled().toString(),
                                "initialHarmonyValue" to initialUseHarmonyValue.toString(),
                            ),
                            type = Daily(),
                        )
                    }
                    harmonyValue != null && legacyValue != null && !harmonyValue.contentEquals(legacyValue) -> {
                        pixel.fire(
                            AUTOFILL_HARMONY_KEY_MISMATCH,
                            mapOf(
                                "key" to keyName,
                                "addWriteGuard" to autofillFeature.addWriteGuard().isEnabled().toString(),
                                "addReadGuard" to autofillFeature.addReadGuard().isEnabled().toString(),
                                "initialHarmonyValue" to initialUseHarmonyValue.toString(),
                                "readFromHarmony" to autofillFeature.readFromHarmony().isEnabled().toString(),
                            ),
                            type = Daily(),
                        )
                        if (autofillFeature.addReadGuard().isEnabled()) {
                            throw SecureStorageException.InternalSecureStorageException("Harmony key mismatch")
                        }
                    }
                    readFromHarmony() -> {
                        return@withContext harmonyValue
                    }
                }
            }

            return@withContext legacyValue
        }
    }

    override suspend fun canUseEncryption(): Boolean = withContext(dispatcherProvider.io()) {
        if (useHarmony()) {
            getEncryptedPreferences() != null && getHarmonyEncryptedPreferences() != null
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
        /*
         * Legacy EncryptedSharedPreferences file. Always used as the source of truth for reads.
         * The original Harmony file with this name might be corrupted, which is why
         * FILENAME_V2 is used as the Harmony destination instead.
         */
        const val FILENAME = "com.duckduckgo.securestorage.store"
        private const val FILENAME_V2 = "${FILENAME}_v2"
    }
}
