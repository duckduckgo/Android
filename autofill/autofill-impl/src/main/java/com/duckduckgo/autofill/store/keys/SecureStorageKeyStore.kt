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

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_HARMONY_KEY_MISMATCH
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_HARMONY_KEY_MISSING
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_GET_KEY_DECODE_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_GET_KEY_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_GET_KEY_NULL_FILE
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_RETRIEVAL_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_UPDATE_KEY_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_HARMONY_PREFERENCES_UPDATE_KEY_NULL_FILE
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PREFERENCES_GET_KEY_DECODE_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PREFERENCES_GET_KEY_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PREFERENCES_GET_KEY_NULL_FILE
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PREFERENCES_KEY_MISSING
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PREFERENCES_UPDATE_KEY_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PREFERENCES_UPDATE_KEY_NULL_FILE
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
        keyValue: ByteArray,
    )

    suspend fun getKey(keyName: String): ByteArray?

    /**
     * This method can be used to check if the keystore implementation has for support for encryption
     *
     * @return `true` if all the crypto dependencies needed by keystore is available and `false` otherwise
     */
    suspend fun canUseEncryption(): Boolean
}

private const val TAG = "RealSecureStorageKeyStore"
class RealSecureStorageKeyStore(
    private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val autofillFeature: AutofillFeature,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val pixel: Pixel,
    private val encryptedPreferencesFactory: EncryptedPreferencesFactory,
) : SecureStorageKeyStore {

    private val mutex: Mutex = Mutex()
    private val harmonyMutex: Mutex = Mutex()

    private var initialUseHarmonyValue: Boolean? = null

    private data class HarmonyFlags(
        val useHarmony: Boolean,
        val readFromHarmony: Boolean,
    )

    private fun harmonyFlags(): HarmonyFlags {
        val useHarmonyFlag = autofillFeature.useHarmony().isEnabled()
        return HarmonyFlags(
            useHarmony = useHarmonyFlag,
            readFromHarmony = (useHarmonyFlag && autofillFeature.readFromHarmony().isEnabled()),
        )
    }

    private val encryptedPreferencesDeferred: Deferred<SharedPreferences?> by lazy {
        coroutineScope.async(dispatcherProvider.io()) {
            val harmonyFlags = harmonyFlags()
            try {
                mutex.withLock {
                    encryptedPreferencesFactory.create(FILENAME)
                }
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                pixel.fire(
                    AutofillPixelNames.AUTOFILL_PREFERENCES_RETRIEVAL_FAILED,
                    getPixelParams(throwable = e, useHarmony = harmonyFlags.useHarmony, readFromHarmony = harmonyFlags.readFromHarmony),
                    type = Daily(),
                )
                null
            }
        }
    }

    private val harmonyPreferencesDeferred: Deferred<SharedPreferences?> by lazy {
        coroutineScope.async(dispatcherProvider.io()) {
            harmonyFlags().let { harmonyFlags ->
                try {
                    harmonyMutex.withLock {
                        initialUseHarmonyValue = harmonyFlags.useHarmony
                        if (harmonyFlags.useHarmony) {
                            getEncryptedPreferences()?.let { legacyPreferences ->
                                sharedPreferencesProvider.getMigratedEncryptedSharedPreferences(legacyPreferences, FILENAME_V3).also {
                                    if (it == null) {
                                        logcat { "autofill harmony preferences retrieval returned null" }
                                    }
                                }
                            } ?: run {
                                logcat { "autofill legacy preferences retrieval returned null. Harmony will also return null" }
                                null
                            }
                        } else {
                            null
                        }
                    }
                } catch (e: Exception) {
                    coroutineContext.ensureActive()
                    pixel.fire(
                        AUTOFILL_HARMONY_PREFERENCES_RETRIEVAL_FAILED,
                        getPixelParams(throwable = e, useHarmony = harmonyFlags.useHarmony, readFromHarmony = harmonyFlags.readFromHarmony),
                        type = Daily(),
                    )
                    logcat { "autofill harmony preferences retrieval failed: $e" }
                    null
                }
            }
        }
    }

    private suspend fun getEncryptedPreferences(): SharedPreferences? {
        return encryptedPreferencesDeferred.await()
    }

    private suspend fun getHarmonyEncryptedPreferences(): SharedPreferences? {
        return harmonyPreferencesDeferred.await()
    }

    @SuppressLint("UseKtx")
    override suspend fun updateKey(
        keyName: String,
        keyValue: ByteArray,
    ) {
        withContext(dispatcherProvider.io()) {
            val harmonyFlags = harmonyFlags()
            val legacyPrefs = getEncryptedPreferences()
            if (legacyPrefs == null) {
                pixel.fire(
                    AUTOFILL_PREFERENCES_UPDATE_KEY_NULL_FILE,
                    getPixelParams(keyName = keyName, useHarmony = harmonyFlags.useHarmony, readFromHarmony = harmonyFlags.readFromHarmony),
                    type = Daily(),
                )
                throw SecureStorageException.InternalSecureStorageException("Legacy Preferences file is null on write")
            }

            fun onLegacyWriteFailure(message: String, cause: Throwable? = null): Nothing {
                pixel.fire(
                    AUTOFILL_PREFERENCES_UPDATE_KEY_FAILED,
                    getPixelParams(
                        keyName = keyName,
                        throwable = cause,
                        useHarmony = harmonyFlags.useHarmony,
                        readFromHarmony = harmonyFlags.readFromHarmony,
                    ),
                    type = Daily(),
                )
                throw SecureStorageException.InternalSecureStorageException(message, cause)
            }

            fun onHarmonyWriteFailure(message: String, cause: Throwable? = null): Nothing {
                pixel.fire(
                    AUTOFILL_HARMONY_PREFERENCES_UPDATE_KEY_FAILED,
                    getPixelParams(
                        keyName = keyName,
                        throwable = cause,
                        useHarmony = harmonyFlags.useHarmony,
                        readFromHarmony = harmonyFlags.readFromHarmony,
                    ),
                    type = Daily(),
                )
                // Rollback legacy write so we don't cause a corrupted state with out-of-sync files
                runCatching {
                    val editor = legacyPrefs.edit()
                    editor.remove(keyName)
                    val committed = editor.commit()
                    if (!committed) {
                        pixel.fire(
                            AutofillPixelNames.AUTOFILL_HARMONY_UPDATE_KEY_ROLLBACK_FAILED,
                            getPixelParams(
                                keyName = keyName,
                                useHarmony = harmonyFlags.useHarmony,
                                readFromHarmony = harmonyFlags.readFromHarmony,
                            ),
                            type = Daily(),
                        )
                    }
                }.onFailure { rollbackError ->
                    pixel.fire(
                        AutofillPixelNames.AUTOFILL_HARMONY_UPDATE_KEY_ROLLBACK_FAILED,
                        getPixelParams(
                            keyName = keyName,
                            throwable = rollbackError,
                            useHarmony = harmonyFlags.useHarmony,
                            readFromHarmony = harmonyFlags.readFromHarmony,
                        ),
                        type = Daily(),
                    )
                }
                throw SecureStorageException.InternalSecureStorageException(message, cause)
            }

            val harmonyPrefs = if (!harmonyFlags.useHarmony) {
                null
            } else {
                getHarmonyEncryptedPreferences().also {
                    if (it == null) {
                        pixel.fire(
                            AUTOFILL_HARMONY_PREFERENCES_UPDATE_KEY_NULL_FILE,
                            getPixelParams(keyName = keyName, useHarmony = harmonyFlags.useHarmony, readFromHarmony = harmonyFlags.readFromHarmony),
                            type = Daily(),
                        )
                        throw SecureStorageException.InternalSecureStorageException("Harmony Preferences file is null on write")
                    }
                }
            }

            // Guard: encryption keys are write-once. If we're trying to write a non-null value
            // for a key that already exists in either store, something upstream read null
            // incorrectly and is about to overwrite a valid key — block the write to prevent
            // irreversible corruption.
            if (keyAlreadyExists(legacyPrefs, harmonyPrefs, keyName, harmonyFlags.useHarmony)) {
                pixel.fire(
                    AUTOFILL_STORE_KEY_ALREADY_EXISTS,
                    getPixelParams(keyName = keyName, useHarmony = harmonyFlags.useHarmony, readFromHarmony = harmonyFlags.readFromHarmony),
                    type = Daily(),
                )
                throw SecureStorageException.InternalSecureStorageException("Trying to overwrite already existing key")
            }

            // Use the editor directly (not the KTX edit(commit=true) extension) so we can capture commit()'s boolean return value
            val legacyCommitted = runCatching {
                val editor = legacyPrefs.edit()
                editor.putString(keyName, keyValue.toByteString().base64())
                editor.commit()
            }.getOrElse {
                ensureActive()
                onLegacyWriteFailure("Error writing to legacy preferences", it)
            }
            if (!legacyCommitted) {
                onLegacyWriteFailure("Legacy commit() returned false — write not persisted to disk")
            }

            if (harmonyPrefs != null && harmonyFlags.useHarmony) {
                val harmonyCommitted = runCatching {
                    val editor = harmonyPrefs.edit()
                    editor.putString(keyName, keyValue.toByteString().base64())
                    editor.commit()
                }.getOrElse {
                    ensureActive()
                    onHarmonyWriteFailure("Error writing to harmony preferences", it)
                }
                if (!harmonyCommitted) {
                    onHarmonyWriteFailure("Harmony commit() returned false — write not persisted to disk")
                }
            }
        }
    }

    /**
     * Checks if the key already exists in either legacy or Harmony preferences.
     * Used as a write guard to prevent overwriting write-once encryption keys.
     */
    private suspend fun keyAlreadyExists(
        legacyPrefs: SharedPreferences?,
        harmonyPrefs: SharedPreferences?,
        keyName: String,
        useHarmony: Boolean,
    ): Boolean {
        val legacyExists = try {
            legacyPrefs?.getString(keyName, null) != null
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            false
        }
        if (legacyExists) return true

        if (useHarmony) {
            val harmonyExists = try {
                harmonyPrefs?.getString(keyName, null) != null
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                // Cannot confirm the key is absent — treat as exists to prevent a potentially destructive overwrite.
                // Genuine Keystore faults reach this path as thrown exceptions; block the write conservatively.
                true
            }
            if (harmonyExists) return true
        }

        return false
    }

    override suspend fun getKey(keyName: String): ByteArray? {
        return withContext(dispatcherProvider.io()) {
            val harmonyFlags = harmonyFlags()

            // Always read from legacy — source of truth

            val legacyPrefs = getEncryptedPreferences().also {
                if (it == null) {
                    pixel.fire(
                        AUTOFILL_PREFERENCES_GET_KEY_NULL_FILE,
                        getPixelParams(keyName = keyName, useHarmony = harmonyFlags.useHarmony, readFromHarmony = harmonyFlags.readFromHarmony),
                        type = Daily(),
                    )
                    if (harmonyFlags.readFromHarmony) {
                        throw SecureStorageException.InternalSecureStorageException("Legacy Preferences file is null on read")
                    }
                }
            }

            val harmonyPrefs = if (!harmonyFlags.useHarmony) {
                null
            } else {
                getHarmonyEncryptedPreferences().also {
                    if (it == null) {
                        pixel.fire(
                            AUTOFILL_HARMONY_PREFERENCES_GET_KEY_NULL_FILE,
                            getPixelParams(keyName = keyName, useHarmony = harmonyFlags.useHarmony, readFromHarmony = harmonyFlags.readFromHarmony),
                            type = Daily(),
                        )
                        if (harmonyFlags.readFromHarmony) {
                            throw SecureStorageException.InternalSecureStorageException("Harmony Preferences file is null on read")
                        }
                    }
                }
            }

            val legacyEncoded = runCatching {
                legacyPrefs?.getString(keyName, null)
            }.getOrElse {
                ensureActive()
                pixel.fire(
                    AUTOFILL_PREFERENCES_GET_KEY_FAILED,
                    getPixelParams(
                        keyName = keyName,
                        throwable = it,
                        useHarmony = harmonyFlags.useHarmony,
                        readFromHarmony = harmonyFlags.readFromHarmony,
                    ),
                    type = Daily(),
                )
                throw it
            }
            val legacyValue: ByteArray? = if (legacyEncoded != null) {
                val decoded = legacyEncoded.decodeBase64()?.toByteArray()
                if (decoded == null) {
                    pixel.fire(
                        AUTOFILL_PREFERENCES_GET_KEY_DECODE_FAILED,
                        getPixelParams(keyName = keyName, useHarmony = harmonyFlags.useHarmony, readFromHarmony = harmonyFlags.readFromHarmony),
                        type = Daily(),
                    )
                    throw SecureStorageException.InternalSecureStorageException("Legacy preferences key value is present but cannot be decoded")
                }
                decoded
            } else {
                null
            }

            // When useHarmony is ON, read Harmony and compare for diagnostic pixels
            if (harmonyFlags.useHarmony) {
                val harmonyEncoded = runCatching {
                    harmonyPrefs?.getString(keyName, null)
                }.getOrElse {
                    ensureActive()
                    pixel.fire(
                        AUTOFILL_HARMONY_PREFERENCES_GET_KEY_FAILED,
                        getPixelParams(
                            keyName = keyName,
                            throwable = it,
                            useHarmony = harmonyFlags.useHarmony,
                            readFromHarmony = harmonyFlags.readFromHarmony,
                        ),
                        type = Daily(),
                    )
                    if (harmonyFlags.readFromHarmony) {
                        throw SecureStorageException.InternalSecureStorageException("Harmony preferences getKey failed")
                    }
                    null
                }
                val harmonyValue: ByteArray? = if (harmonyEncoded != null) {
                    val decoded = harmonyEncoded.decodeBase64()?.toByteArray()
                    if (decoded == null) {
                        pixel.fire(
                            AUTOFILL_HARMONY_PREFERENCES_GET_KEY_DECODE_FAILED,
                            getPixelParams(keyName = keyName, useHarmony = harmonyFlags.useHarmony, readFromHarmony = harmonyFlags.readFromHarmony),
                            type = Daily(),
                        )
                        if (harmonyFlags.readFromHarmony) {
                            throw SecureStorageException.InternalSecureStorageException(
                                "Harmony preferences key value is present but cannot be decoded",
                            )
                        }
                        null
                    } else {
                        decoded
                    }
                } else {
                    null
                }

                when {
                    harmonyPrefs != null && harmonyValue == null && legacyValue != null -> {
                        pixel.fire(
                            AUTOFILL_HARMONY_KEY_MISSING,
                            getPixelParams(keyName = keyName, useHarmony = harmonyFlags.useHarmony, readFromHarmony = harmonyFlags.readFromHarmony),
                            type = Daily(),
                        )
                        if (harmonyFlags.readFromHarmony) {
                            throw SecureStorageException.InternalSecureStorageException("Harmony key missing")
                        }
                    }
                    legacyPrefs != null && harmonyValue != null && legacyValue == null -> {
                        pixel.fire(
                            AUTOFILL_PREFERENCES_KEY_MISSING,
                            getPixelParams(keyName = keyName, useHarmony = harmonyFlags.useHarmony, readFromHarmony = harmonyFlags.readFromHarmony),
                            type = Daily(),
                        )
                        if (harmonyFlags.readFromHarmony) {
                            throw SecureStorageException.InternalSecureStorageException("Legacy key missing")
                        }
                    }
                    harmonyValue != null && legacyValue != null && !harmonyValue.contentEquals(legacyValue) -> {
                        pixel.fire(
                            AUTOFILL_HARMONY_KEY_MISMATCH,
                            getPixelParams(keyName = keyName, useHarmony = harmonyFlags.useHarmony, readFromHarmony = harmonyFlags.readFromHarmony),
                            type = Daily(),
                        )
                        if (harmonyFlags.readFromHarmony) {
                            throw SecureStorageException.InternalSecureStorageException("Harmony key mismatch")
                        }
                    }
                    harmonyFlags.readFromHarmony -> {
                        logcat(TAG) { "Returning $keyName from harmony" }
                        return@withContext harmonyValue
                    }
                }
            }
            logcat(TAG) { "Returning $keyName from legacy" }
            return@withContext legacyValue
        }
    }

    override suspend fun canUseEncryption(): Boolean = withContext(dispatcherProvider.io()) {
        if (harmonyFlags().useHarmony) {
            getEncryptedPreferences() != null && getHarmonyEncryptedPreferences() != null
        } else {
            getEncryptedPreferences() != null
        }
    }

    private fun getPixelParams(keyName: String? = null, throwable: Throwable? = null, useHarmony: Boolean, readFromHarmony: Boolean) = buildMap {
        keyName?.let { put("key", it) }
        put("useHarmony", useHarmony.toString())
        put("initialHarmonyValue", initialUseHarmonyValue.toString())
        put("readFromHarmony", readFromHarmony.toString())
        throwable?.error()?.let { put("error", it) }
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
         * FILENAME_V2: Harmony destination for minSupportedVersion 5.271.0 — potentially stale for
         * users whose harmony was disabled mid-rollout while legacy continued to receive writes.
         * FILENAME_V3: Harmony destination for minSupportedVersion 5.274.0 — always freshly migrated
         * from legacy, ensuring a consistent starting state regardless of prior rollout history.
         */
        const val FILENAME = "com.duckduckgo.securestorage.store"
        private const val FILENAME_V3 = "${FILENAME}_v3"
    }
}
