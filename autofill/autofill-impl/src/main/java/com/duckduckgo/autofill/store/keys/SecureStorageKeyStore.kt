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
import com.duckduckgo.autofill.impl.service.AutofillServiceFeature
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
        vararg keyValues: Pair<String, ByteArray>,
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
    private val autofillServiceFeature: AutofillServiceFeature,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val pixel: Pixel,
    private val encryptedPreferencesFactory: EncryptedPreferencesFactory,
) : SecureStorageKeyStore {

    private val mutex: Mutex = Mutex()
    private val harmonyMutex: Mutex = Mutex()

    private var initialUseHarmonyValue: Boolean? = null
    private var initialMultiProcessValue: Boolean? = null

    /**
     * Evaluates all three harmony-related flags in a single consistent snapshot.
     * [multiProcess] (autofill service enabled) implies both [useHarmony] and [readFromHarmony]:
     * Harmony is the only multi-process-safe store, so it is treated as implicitly active regardless
     * of the remote-config flag state. All three values are derived from one check of each flag,
     * guaranteeing they can never be mutually inconsistent.
     *
     * The [multiProcess] flag is latched: once true, it stays true for the lifetime of this instance.
     * This prevents mid-execution flag changes from causing data corruption — if we ever operated in
     * multi-process mode, we must continue checking Harmony for key existence to avoid overwriting
     * keys that may only exist there.
     */
    private data class HarmonyFlags(
        val useHarmony: Boolean,
        val readFromHarmony: Boolean,
        val multiProcess: Boolean,
    )

    private suspend fun harmonyFlags(): HarmonyFlags {
        return withContext(dispatcherProvider.io()) {
            val autofillServiceFlag = autofillServiceFeature.self().isEnabled()
            val useHarmonyFlag = autofillFeature.useHarmony().isEnabled()

            // Latch: once multiProcess is true, it stays true for this session to prevent
            // mid-execution flag changes from causing us to miss keys that exist only in Harmony.
            if (initialMultiProcessValue == null) {
                initialMultiProcessValue = autofillServiceFlag
            }
            val effectiveMultiProcess = initialMultiProcessValue == true || autofillServiceFlag

            HarmonyFlags(
                useHarmony = useHarmonyFlag || effectiveMultiProcess,
                readFromHarmony = (useHarmonyFlag && autofillFeature.readFromHarmony().isEnabled()) || effectiveMultiProcess,
                multiProcess = effectiveMultiProcess,
            )
        }
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
            val harmonyFlags = harmonyFlags()
            try {
                harmonyMutex.withLock {
                    initialUseHarmonyValue = harmonyFlags.useHarmony
                    if (!harmonyFlags.useHarmony) {
                        null
                    } else {
                        // Migration reads from legacy (safe in multi-process) and writes to harmony.
                        // If both processes race to migrate, they read identical data and write
                        // identical keys — idempotent and safe under Harmony's multi-process locking.
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

    private suspend fun getEncryptedPreferences(): SharedPreferences? {
        return encryptedPreferencesDeferred.await()
    }

    private suspend fun getHarmonyEncryptedPreferences(): SharedPreferences? {
        return harmonyPreferencesDeferred.await()
    }

    private fun Array<out Pair<String, ByteArray>>.getKeys(): String =
        this.map { it.first }.toString()

    @SuppressLint("UseKtx")
    override suspend fun updateKey(
        vararg keyValues: Pair<String, ByteArray>,
    ) {
        withContext(dispatcherProvider.io()) {
            val harmonyFlags = harmonyFlags()

            // Always write to legacy for rollback support. In multi-process mode, keyAlreadyExists()
            // uses Harmony as the source of truth (not legacy) to avoid stale cache issues. The
            // KeyAlreadyExistsException guard prevents divergent writes across processes.
            val legacyPrefs = getEncryptedPreferences().also {
                if (it == null) {
                    pixel.fire(
                        AUTOFILL_PREFERENCES_UPDATE_KEY_NULL_FILE,
                        getPixelParams(
                            keyName = keyValues.getKeys(),
                            useHarmony = harmonyFlags.useHarmony,
                            readFromHarmony = harmonyFlags.readFromHarmony,
                        ),
                        type = Daily(),
                    )
                    throw SecureStorageException.InternalSecureStorageException("Legacy Preferences file is null on write")
                }
            }

            val harmonyPrefs = if (!harmonyFlags.useHarmony) {
                null
            } else {
                getHarmonyEncryptedPreferences().also {
                    if (it == null) {
                        pixel.fire(
                            AUTOFILL_HARMONY_PREFERENCES_UPDATE_KEY_NULL_FILE,
                            getPixelParams(
                                keyName = keyValues.getKeys(),
                                useHarmony = harmonyFlags.useHarmony,
                                readFromHarmony = harmonyFlags.readFromHarmony,
                            ),
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
            if (keyAlreadyExists(legacyPrefs, harmonyPrefs, keyName, harmonyFlags)) {
                pixel.fire(
                    AUTOFILL_STORE_KEY_ALREADY_EXISTS,
                    getPixelParams(
                        keyName = keyValue.first,
                        useHarmony = harmonyFlags.useHarmony,
                        readFromHarmony = harmonyFlags.readFromHarmony,
                    ),
                    type = Daily(),
                )
                throw SecureStorageException.KeyAlreadyExistsException("Trying to overwrite already existing key")
            }

            if (legacyPrefs != null) {
                // Use the editor directly (not the KTX edit(commit=true) extension) so we can capture commit()'s boolean return value
                val (legacyCommitted, error) = runCatching {
                    logcat(TAG) { "Writing $keyName to legacy" }
                    val editor = legacyPrefs.edit()
                    keyValues.forEach { keyValue ->
                        editor.putString(keyValue.first, keyValue.second.toByteString().base64())
                    }
                    editor.commit() to null
                }.getOrElse {
                    ensureActive()
                    false to it
                }
                if (!legacyCommitted) {
                    pixel.fire(
                        AUTOFILL_PREFERENCES_UPDATE_KEY_FAILED,
                        getPixelParams(
                            keyValues.getKeys(),
                            throwable = error,
                            useHarmony = harmonyFlags.useHarmony,
                            readFromHarmony = harmonyFlags.readFromHarmony,
                        ),
                        type = Daily(),
                    )
                    throw SecureStorageException.InternalSecureStorageException("Legacy commit() returned false — write not persisted to disk", error)
                }
            }

            if (harmonyPrefs != null && harmonyFlags.useHarmony) {
                val (harmonyCommitted, error) = runCatching {
                    logcat(TAG) { "Writing $keyName to harmony" }
                    val editor = harmonyPrefs.edit()
                    keyValues.forEach { keyValue ->
                        editor.putString(keyValue.first, keyValue.second.toByteString().base64())
                    }
                    editor.commit() to null
                }.getOrElse {
                    ensureActive()
                    false to it
                }
                if (!harmonyCommitted) {
                    pixel.fire(
                        AUTOFILL_HARMONY_PREFERENCES_UPDATE_KEY_FAILED,
                        getPixelParams(
                            keyName = keyValues.getKeys(),
                            throwable = error,
                            useHarmony = harmonyFlags.useHarmony,
                            readFromHarmony = harmonyFlags.readFromHarmony,
                        ),
                        type = Daily(),
                    )
                    // Rollback legacy write so we don't cause a corrupted state with out-of-sync files.
                    if (legacyPrefs != null) {
                        runCatching {
                            val editor = legacyPrefs.edit()
                            keyValues.forEach { keyValue ->
                                editor.remove(keyValue.first)
                            }
                            val committed = editor.commit()
                            if (!committed) {
                                pixel.fire(
                                    AutofillPixelNames.AUTOFILL_HARMONY_UPDATE_KEY_ROLLBACK_FAILED,
                                    getPixelParams(
                                        keyName = keyValues.getKeys(),
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
                                    keyName = keyValues.getKeys(),
                                    throwable = rollbackError,
                                    useHarmony = harmonyFlags.useHarmony,
                                    readFromHarmony = harmonyFlags.readFromHarmony,
                                ),
                                type = Daily(),
                            )
                        }
                    }
                    throw SecureStorageException.InternalSecureStorageException(
                        "Error writing to harmony preferences",
                        error,
                    )
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
        harmonyFlags: HarmonyFlags,
    ): Boolean {
        val legacyExists = try {
            if (harmonyFlags.multiProcess) {
                false
            } else {
                legacyPrefs?.getString(keyName, null) != null
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            false
        }
        if (legacyExists) return true

        if (harmonyFlags.useHarmony) {
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

            // In multi-process mode, skip legacy reads — EncryptedSharedPreferences writes do not
            // propagate between processes, so legacy may be stale. readFromHarmony() returns true
            // in this mode, so the existing readFromHarmony path returns the harmony value below.
            val legacyPrefs: SharedPreferences? = if (!harmonyFlags.multiProcess) {
                getEncryptedPreferences().also {
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
            } else {
                null
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

            val legacyEncoded = if (legacyPrefs != null) {
                runCatching {
                    legacyPrefs.getString(keyName, null)
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
            } else {
                null
            }

            val legacyValue: ByteArray? = legacyEncoded?.let {
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
                    harmonyPrefs != null && harmonyEncoded == null && legacyValue != null -> {
                        pixel.fire(
                            AUTOFILL_HARMONY_KEY_MISSING,
                            getPixelParams(keyName = keyName, useHarmony = harmonyFlags.useHarmony, readFromHarmony = harmonyFlags.readFromHarmony),
                            type = Daily(),
                        )
                        if (harmonyFlags.readFromHarmony) {
                            throw SecureStorageException.InternalSecureStorageException("Harmony key missing")
                        }
                    }
                    legacyPrefs != null && harmonyValue != null && legacyEncoded == null -> {
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
        val harmonyFlags = harmonyFlags()
        when {
            harmonyFlags.multiProcess -> getHarmonyEncryptedPreferences() != null
            harmonyFlags.useHarmony && !harmonyFlags.multiProcess -> getEncryptedPreferences() != null && getHarmonyEncryptedPreferences() != null
            else -> getEncryptedPreferences() != null
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
